package com.match.registry.service;

import com.match.registry.persistence.ImageArtifactMapper;
import com.match.registry.persistence.ImageArtifactRecord;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import com.match.registry.persistence.ImageReleaseMapper;
import com.match.registry.persistence.ImageReleaseRecord;
import com.match.registry.persistence.ImageUploadMapper;
import com.match.registry.persistence.ImageUploadRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

@Service
public class AgentImageArchiveService {
    private final ImageDeploymentMapper deployments;
    private final ImageReleaseMapper releases;
    private final ImageArtifactMapper artifacts;
    private final ImageUploadMapper uploads;
    private final Path stagingRoot;
    private final ImageFileService imageFiles;

    @Autowired
    public AgentImageArchiveService(ImageDeploymentMapper deployments, ImageReleaseMapper releases,
                                    ImageArtifactMapper artifacts, ImageUploadMapper uploads,
                                    @Value("${xkp.registry.staging-dir:${XKP_REGISTRY_STAGING_DIR:/data/registry-staging}}") String stagingRoot,
                                    ImageFileService imageFiles) {
        this.deployments = deployments;
        this.releases = releases;
        this.artifacts = artifacts;
        this.uploads = uploads;
        this.stagingRoot = Paths.get(stagingRoot).toAbsolutePath().normalize();
        this.imageFiles = imageFiles;
    }

    public AgentImageArchiveService(ImageDeploymentMapper deployments, ImageReleaseMapper releases,
                                    ImageArtifactMapper artifacts, ImageUploadMapper uploads, String stagingRoot) {
        this(deployments, releases, artifacts, uploads, stagingRoot, null);
    }

    public Path archive(String agentId, String deploymentId) {
        ImageDeploymentRecord deployment = deployments.selectById(deploymentId);
        if (deployment == null || !agentId.equals(deployment.getAgentId())
                || (!"PENDING".equals(deployment.getState()) && !"PULLED".equals(deployment.getState())
                && !"RUNNING".equals(deployment.getState()))) {
            throw new IllegalArgumentException("镜像推送任务不可用");
        }
        if (deployment.getFileId() != null) return imageFiles.archive(imageFiles.get(deployment.getFileId()));
        ImageReleaseRecord release = releases.selectById(deployment.getReleaseId());
        ImageArtifactRecord artifact = release == null ? null : artifacts.selectById(release.getArtifactId());
        ImageUploadRecord upload = artifact == null ? null : uploads.selectByArtifactId(artifact.getArtifactId());
        if (upload == null || upload.getFinalSha256() == null) throw new IllegalArgumentException("镜像归档尚未就绪");
        Path archive = findArchive(upload);
        if (!archive.startsWith(stagingRoot) || !Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("镜像归档不存在，请重新上传并发布该版本");
        }
        return archive;
    }

    public String internalLocation(String agentId, String deploymentId) {
        Path path = archive(agentId, deploymentId).toAbsolutePath().normalize();
        if (!path.startsWith(stagingRoot)) throw new IllegalArgumentException("镜像归档路径无效");
        Path relative = stagingRoot.relativize(path);
        StringBuilder location = new StringBuilder("/_protected/image-archives/");
        for (Path part : relative) {
            if (location.charAt(location.length() - 1) != '/') location.append('/');
            location.append(UriUtils.encodePathSegment(part.toString(), StandardCharsets.UTF_8));
        }
        return location.toString();
    }

    private Path findArchive(ImageUploadRecord upload) {
        Path direct = stagingRoot.resolve(upload.getUploadId()).resolve("archive").normalize();
        if (direct.startsWith(stagingRoot) && Files.isRegularFile(direct)) return direct;
        String sha = upload.getFinalSha256() == null ? upload.getExpectedSha256() : upload.getFinalSha256();
        if (sha == null) return direct;
        List<ImageUploadRecord> matches = uploads.selectByFinalSha256(sha);
        if (matches != null) for (ImageUploadRecord match : matches) {
            Path candidate = stagingRoot.resolve(match.getUploadId()).resolve("archive").normalize();
            if (candidate.startsWith(stagingRoot) && Files.isRegularFile(candidate)) return candidate;
        }
        return direct;
    }

    public boolean availableForArtifact(String artifactId) {
        ImageUploadRecord upload = artifactId == null ? null : uploads.selectByArtifactId(artifactId);
        if (upload == null || upload.getFinalSha256() == null) return false;
        Path archive = findArchive(upload);
        try {
            return archive.startsWith(stagingRoot) && Files.isRegularFile(archive) && Files.size(archive) > 0;
        } catch (java.io.IOException ignored) {
            return false;
        }
    }
}
