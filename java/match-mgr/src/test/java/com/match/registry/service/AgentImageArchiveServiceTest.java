package com.match.registry.service;

import com.match.registry.persistence.*;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AgentImageArchiveServiceTest {
    @Test public void servesArchiveFromConfiguredPersistentStagingRoot() throws Exception {
        Path root=Files.createTempDirectory("registry-staging-"); Path archive=root.resolve("upload-1/archive"); Files.createDirectories(archive.getParent()); Files.write(archive,new byte[]{1});
        ImageDeploymentMapper deployments=mock(ImageDeploymentMapper.class); ImageReleaseMapper releases=mock(ImageReleaseMapper.class); ImageArtifactMapper artifacts=mock(ImageArtifactMapper.class); ImageUploadMapper uploads=mock(ImageUploadMapper.class);
        ImageDeploymentRecord deployment=new ImageDeploymentRecord(); deployment.setDeploymentId("deployment-1"); deployment.setAgentId("agent-1"); deployment.setReleaseId("release-1"); deployment.setState("PENDING");
        ImageReleaseRecord release=new ImageReleaseRecord(); release.setArtifactId("artifact-1"); ImageArtifactRecord artifact=new ImageArtifactRecord(); artifact.setArtifactId("artifact-1"); ImageUploadRecord upload=new ImageUploadRecord(); upload.setUploadId("upload-1"); upload.setFinalSha256("a");
        when(deployments.selectById("deployment-1")).thenReturn(deployment); when(releases.selectById("release-1")).thenReturn(release); when(artifacts.selectById("artifact-1")).thenReturn(artifact); when(uploads.selectByArtifactId("artifact-1")).thenReturn(upload);
        assertEquals(archive,new AgentImageArchiveService(deployments,releases,artifacts,uploads,root.toString()).archive("agent-1","deployment-1"));
    }
}
