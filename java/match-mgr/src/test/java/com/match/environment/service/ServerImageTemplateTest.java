package com.match.environment.service;

import com.match.agent.model.AgentDockerImageView;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.licensing.guard.LicenseGuard;
import com.match.registry.service.DockerInventoryService;
import org.junit.Test;
import java.time.Clock;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ServerImageTemplateTest {
    @Test public void pinsInspectedImageAndNeverUsesClientDigestOrRelease() {
        ContainerTemplateMapper mapper = mock(ContainerTemplateMapper.class);
        DockerInventoryService inventory = mock(DockerInventoryService.class);
        ContainerTemplateService service = new ContainerTemplateService(mapper, mock(LicenseGuard.class), Clock.systemUTC());
        service.setDockerInventoryService(inventory);
        com.match.registry.persistence.ImageFileMapper files = mock(com.match.registry.persistence.ImageFileMapper.class);
        service.setImageFileMapper(files);
        AgentDockerImageView actual = new AgentDockerImageView();
        actual.setRepository("xkp/test"); actual.setTag("latest");
        actual.setId("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        actual.setRepoTags(java.util.Arrays.asList("xkp/test:latest", "xkp/files/upload:latest"));
        com.match.registry.persistence.ImageFileRecord source = new com.match.registry.persistence.ImageFileRecord(); source.setFileId("file");
        when(files.selectByImageReference("xkp/files/upload:latest")).thenReturn(source);
        when(inventory.inspectedImage("agent", "inspection", "xkp/test:latest", 7)).thenReturn(actual);
        ContainerTemplateRequest request = new ContainerTemplateRequest();
        request.setAgentId("agent"); request.setInspectionCommandId("inspection");
        request.setTemplateName("test"); request.setComponentType("ANNOTATION");
        request.setImageReference("xkp/test:latest"); request.setRuntimeName("sysbox-runc");
        request.setRestartPolicy("always"); request.setMountTarget("/root/data");
        ContainerTemplateRecord result = service.publish(request, 7);
        assertEquals(actual.getId(), result.getImageId());
        assertEquals("file", result.getImageFileId());
        assertEquals("xkp/test:latest", result.getImageReference());
        verify(mapper, never()).selectPublishedDigest(anyString(), anyString());
    }
}
