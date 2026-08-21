package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageReleaseMapper;
import com.match.registry.persistence.ImageReleaseRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ImageReleaseService {
    private static final Pattern DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private final ImageArtifactMapper artifacts;
    private final ImageReleaseMapper releases;
    private final Clock clock;

    public ImageReleaseService(ImageArtifactMapper artifacts, ImageReleaseMapper releases) {
        this(artifacts, releases, Clock.systemUTC());
    }

    public ImageReleaseService(ImageArtifactMapper artifacts, ImageReleaseMapper releases, Clock clock) {
        this.artifacts = artifacts;
        this.releases = releases;
        this.clock = clock;
    }

    @Transactional
    public ImageReleaseRecord publish(String role, String artifactId, int actorId) {
        requireSuperAdmin(role);
        ImageArtifactRecord artifact = artifacts.selectById(artifactId);
        if (artifact == null || !"APPROVED".equals(artifact.getReviewState())
                || !"READY".equals(artifact.getImportState())) {
            throw new IllegalArgumentException("ARTIFACT_NOT_READY");
        }
        String digest = artifact.getRegistryDigest();
        if (digest == null || !DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException("IMMUTABLE_DIGEST_REQUIRED");
        }
        ImageReleaseRecord release = new ImageReleaseRecord();
        release.setReleaseId(UUID.randomUUID().toString());
        release.setGroupId(artifact.getGroupId());
        release.setComponentType(normalizeComponent(artifact.getComponentType()));
        release.setArtifactId(artifact.getArtifactId());
        release.setRegistryDigest(digest);
        release.setVersion(artifact.getVersion());
        release.setState("PUBLISHED");
        release.setCreatedBy(actorId);
        release.setCreatedAt(LocalDateTime.now(clock));
        release.setPublishedAt(release.getCreatedAt());
        releases.insert(release);
        return release;
    }

    public ImageReleaseRecord release(String role, String releaseId) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new IllegalArgumentException("ADMIN_REQUIRED");
        }
        return releases.selectById(releaseId);
    }

    private void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("SUPER_ADMIN_REQUIRED");
    }

    private String normalizeComponent(String value) {
        if (value == null) throw new IllegalArgumentException("COMPONENT_REQUIRED");
        String component = value.toUpperCase(Locale.ROOT);
        if (!"ANNOTATION".equals(component) && !"EDITOR".equals(component)) {
            throw new IllegalArgumentException("INVALID_COMPONENT");
        }
        return component;
    }
}
