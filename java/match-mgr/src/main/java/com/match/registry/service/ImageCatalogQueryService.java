package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import com.match.registry.persistence.ImageGroupMapper;
import com.match.registry.persistence.ImageGroupRecord;
import com.match.registry.persistence.ImageReleaseMapper;
import com.match.registry.persistence.ImageReleaseRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImageCatalogQueryService {
    private final ImageGroupMapper groups;
    private final ImageArtifactMapper artifacts;
    private final ImageReleaseMapper releases;
    private final ImageDeploymentMapper deployments;

    public ImageCatalogQueryService(ImageGroupMapper groups, ImageArtifactMapper artifacts,
                                    ImageReleaseMapper releases, ImageDeploymentMapper deployments) {
        this.groups = groups; this.artifacts = artifacts; this.releases = releases; this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public List<ImageGroupRecord> groups(String role, Integer limit) {
        requireAdmin(role); return groups.selectVisible(bound(limit));
    }
    @Transactional(readOnly = true)
    public List<ImageArtifactRecord> artifacts(String role, Integer limit) {
        requireAdmin(role); return artifacts.selectVisible(bound(limit));
    }
    @Transactional(readOnly = true)
    public List<ImageReleaseRecord> releases(String role, Integer limit) {
        requireAdmin(role); return releases.selectVisible(bound(limit));
    }
    @Transactional(readOnly = true)
    public List<ImageDeploymentRecord> deployments(String role, Integer limit) {
        requireAdmin(role); return deployments.selectVisible(bound(limit));
    }

    private <T> List<T> cap(List<T> values, Integer limit) {
        int requested = limit == null ? 50 : limit;
        if (requested < 1) requested = 1;
        int end = Math.min(Math.min(requested, 100), values.size());
        return values.subList(0, end);
    }
    private int bound(Integer limit) {
        int requested = limit == null ? 50 : limit;
        if (requested < 1) requested = 1;
        return Math.min(requested, 100);
    }
    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("ADMIN_REQUIRED");
    }
}
