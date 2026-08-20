package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnvironmentCommandFactoryTest {
    @Test
    public void rebuildsLifecyclePayloadFromPinnedTemplatesAndSlotPorts() throws Exception {
        ContainerTemplateMapper templateMapper = mock(ContainerTemplateMapper.class);
        EnvironmentPortAllocationMapper portMapper = mock(EnvironmentPortAllocationMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EnvironmentCommandFactory factory = new EnvironmentCommandFactory(templateMapper, portMapper, objectMapper);
        TrainingEnvironmentRecord environment = environment();
        when(templateMapper.selectVersion("annotation-template", 3))
                .thenReturn(template("annotation-template", 3, "ANNOTATION", "/root/data"));
        when(templateMapper.selectVersion("editor-template", 5))
                .thenReturn(template("editor-template", 5, "EDITOR", "/home/student/data"));
        when(portMapper.selectBySlot("slot-1")).thenReturn(Arrays.asList(
                port("ANNOTATION", 8080, 8081), port("EDITOR", 9090, 9091)));

        EnvironmentCommandPayload payload = objectMapper.readValue(
                factory.createPayloadJson(environment, "operation-1"), EnvironmentCommandPayload.class);

        assertEquals("environment-1", payload.getEnvironmentId());
        assertEquals("operation-1", payload.getOperationId());
        assertEquals("training/21/31", payload.getWorkspaceRelativePath());
        assertEquals("/root/data", payload.getComponents().get(0).getMountTarget());
        assertEquals(Integer.valueOf(8081), payload.getComponents().get(0).getPorts().get(0).getHostPort());
        assertEquals("/home/student/data", payload.getComponents().get(1).getMountTarget());
        assertEquals(Integer.valueOf(9091), payload.getComponents().get(1).getPorts().get(0).getHostPort());
    }

    private TrainingEnvironmentRecord environment() {
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("environment-1");
        environment.setSlotId("slot-1");
        environment.setWorkspaceRelativePath("training/21/31");
        environment.setAnnotationTemplateId("annotation-template");
        environment.setAnnotationTemplateVersion(3);
        environment.setAnnotationContainerName("annotation-1");
        environment.setEditorTemplateId("editor-template");
        environment.setEditorTemplateVersion(5);
        environment.setEditorContainerName("editor-1");
        return environment;
    }

    private ContainerTemplateRecord template(String id, int version, String type, String mountTarget) {
        ContainerTemplateRecord template = new ContainerTemplateRecord();
        template.setTemplateId(id);
        template.setTemplateVersion(version);
        template.setComponentType(type);
        template.setImageReference("xkp/" + type.toLowerCase() + ":v1");
        template.setRuntimeName("EDITOR".equals(type) ? "nvidia" : "sysbox-runc");
        template.setRestartPolicy("always");
        template.setMountTarget(mountTarget);
        template.setConfigFingerprint(type.toLowerCase() + "-fingerprint");
        template.setGpuEnabled("EDITOR".equals(type));
        return template;
    }

    private EnvironmentPortAllocationRecord port(String type, int containerPort, int hostPort) {
        EnvironmentPortAllocationRecord port = new EnvironmentPortAllocationRecord();
        port.setComponentType(type);
        port.setContainerPort(containerPort);
        port.setHostPort(hostPort);
        port.setProtocol("tcp");
        return port;
    }
}
