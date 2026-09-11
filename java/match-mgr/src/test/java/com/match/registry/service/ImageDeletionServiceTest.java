package com.match.registry.service;

import com.match.registry.persistence.*;
import org.junit.Test;
import java.util.Collections;
import static org.mockito.Mockito.*;

public class ImageDeletionServiceTest {
    @Test public void publishedArtifactDeletionRemovesAllPlatformRecords() {
        ImageArtifactMapper artifacts=mock(ImageArtifactMapper.class); ImageUploadMapper uploads=mock(ImageUploadMapper.class);
        ImageUploadChunkMapper chunks=mock(ImageUploadChunkMapper.class); ImageReleaseMapper releases=mock(ImageReleaseMapper.class);
        ImageDeploymentMapper deployments=mock(ImageDeploymentMapper.class); ImageUploadService files=mock(ImageUploadService.class);
        ImageArtifactRecord artifact=new ImageArtifactRecord(); artifact.setArtifactId("artifact-1");
        ImageUploadRecord upload=new ImageUploadRecord(); upload.setUploadId("upload-1"); upload.setArtifactId("artifact-1");
        ImageReleaseRecord release=new ImageReleaseRecord(); release.setReleaseId("release-1"); release.setArtifactId("artifact-1");
        when(artifacts.selectById("artifact-1")).thenReturn(artifact); when(uploads.selectByArtifactId("artifact-1")).thenReturn(upload);
        when(releases.selectByArtifactId("artifact-1")).thenReturn(Collections.singletonList(release));
        new ImageDeletionService(artifacts,uploads,chunks,releases,deployments,files).delete("SUPER_ADMIN","artifact-1");
        verify(deployments).deleteByReleaseId("release-1"); verify(releases).deleteById("release-1");
        verify(chunks).deleteByUpload("upload-1"); verify(uploads).deleteById("upload-1"); verify(artifacts).deleteById("artifact-1");
        verify(files).cleanupStaging("upload-1");
    }
}
