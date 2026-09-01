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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class AgentImageArchiveService {
    private final ImageDeploymentMapper deployments;
    private final ImageReleaseMapper releases;
    private final ImageArtifactMapper artifacts;
    private final ImageUploadMapper uploads;
    private final Path stagingRoot;

    public AgentImageArchiveService(ImageDeploymentMapper deployments, ImageReleaseMapper releases,
                                    ImageArtifactMapper artifacts, ImageUploadMapper uploads,
                                    @Value("${xkp.registry.staging-dir:${java.io.tmpdir}/xkp-registry-staging}") String stagingRoot) {
        this.deployments = deployments;
        this.releases = releases;
        this.artifacts = artifacts;
        this.uploads = uploads;
        this.stagingRoot = Paths.get(stagingRoot).toAbsolutePath().normalize();
    }

    public Path archive(String agentId, String deploymentId) {
        ImageDeploymentRecord deployment = deployments.selectById(deploymentId);
        if (deployment == null || !agentId.equals(deployment.getAgentId())
                || (!"PENDING".equals(deployment.getState()) && !"PULLED".equals(deployment.getState())
                && !"RUNNING".equals(deployment.getState()))) {
            throw new IllegalArgumentException("镜像推送任务不可用");
        }
        ImageReleaseRecord release = releases.selectById(deployment.getReleaseId());
        ImageArtifactRecord artifact = release == null ? null : artifacts.selectById(release.getArtifactId());
        ImageUploadRecord upload = artifact == null ? null : uploads.selectByArtifactId(artifact.getArtifactId());
        if (upload == null || upload.getFinalSha256() == null) {
            throw new IllegalArgumentException("镜像归档尚未就绪");
        }
        Path archive = stagingRoot.resolve(upload.getUploadId()).resolve("archive").normalize();
        if (!archive.startsWith(stagingRoot) || !Files.isRegularFile(archive)) {
            throw new IllegalArgumentException("镜像归档不存在");
        }
        return archive;
    }
}
