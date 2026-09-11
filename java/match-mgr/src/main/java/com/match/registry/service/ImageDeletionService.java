package com.match.registry.service;

import com.match.registry.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ImageDeletionService {
    private final ImageArtifactMapper artifacts; private final ImageUploadMapper uploads;
    private final ImageUploadChunkMapper chunks; private final ImageReleaseMapper releases;
    private final ImageDeploymentMapper deployments; private final ImageUploadService files;
    public ImageDeletionService(ImageArtifactMapper artifacts, ImageUploadMapper uploads, ImageUploadChunkMapper chunks,
                                ImageReleaseMapper releases, ImageDeploymentMapper deployments, ImageUploadService files) {
        this.artifacts=artifacts; this.uploads=uploads; this.chunks=chunks; this.releases=releases; this.deployments=deployments; this.files=files;
    }
    @Transactional public void delete(String role,String artifactId) {
        if (!"SUPER_ADMIN".equals(role)) throw new IllegalArgumentException("SUPER_ADMIN_REQUIRED");
        ImageArtifactRecord artifact=artifacts.selectById(artifactId); if(artifact==null) throw new IllegalArgumentException("镜像任务不存在");
        java.util.List<ImageReleaseRecord> published=releases.selectByArtifactId(artifactId);
        if(published!=null) for(ImageReleaseRecord release:published){deployments.deleteByReleaseId(release.getReleaseId());releases.deleteById(release.getReleaseId());}
        ImageUploadRecord upload=uploads.selectByArtifactId(artifactId);
        if(upload!=null){chunks.deleteByUpload(upload.getUploadId());uploads.deleteById(upload.getUploadId());afterCommit(()->files.cleanupStaging(upload.getUploadId()));}
        artifacts.deleteById(artifactId);
    }
    private void afterCommit(Runnable action){if(!TransactionSynchronizationManager.isSynchronizationActive()){action.run();return;}TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});}
}
