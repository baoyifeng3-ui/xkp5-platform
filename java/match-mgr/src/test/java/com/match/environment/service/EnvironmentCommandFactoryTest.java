package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
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

    @Test
    public void buildsCompetitionPayloadWithStablePairNamesFromPinnedTemplates() throws Exception {
        ContainerTemplateMapper templateMapper = mock(ContainerTemplateMapper.class);
        EnvironmentPortAllocationMapper portMapper = mock(EnvironmentPortAllocationMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EnvironmentCommandFactory factory = new EnvironmentCommandFactory(templateMapper, portMapper, objectMapper);
        CompetitionEnvironmentRecord environment = competitionEnvironment();
        when(templateMapper.selectVersion("annotation-template", 3))
                .thenReturn(template("annotation-template", 3, "ANNOTATION", "/root/data"));
        when(templateMapper.selectVersion("editor-template", 5))
                .thenReturn(template("editor-template", 5, "EDITOR", "/home/student/data"));
        when(portMapper.selectBySlot("slot-2")).thenReturn(Arrays.asList(
                port("ANNOTATION", 8080, 8082), port("EDITOR", 9090, 9092)));

        EnvironmentCommandPayload payload = objectMapper.readValue(
                factory.createPayloadJson(environment, "operation-2"), EnvironmentCommandPayload.class);

        assertEquals("competition-environment-2", payload.getEnvironmentId());
        assertEquals("operation-2", payload.getOperationId());
        assertEquals("competition/slot-2", payload.getWorkspaceRelativePath());
        assertEquals("xkp-comp-11111111-s2-annotation",
                payload.getComponents().get(0).getContainerName());
        assertEquals("annotation-fingerprint", payload.getComponents().get(0).getConfigFingerprint());
        assertEquals("xkp-comp-11111111-s2-editor",
                payload.getComponents().get(1).getContainerName());
        assertEquals("editor-fingerprint", payload.getComponents().get(1).getConfigFingerprint());
    }

    @Test
    public void rejectsCompetitionPayloadWhenPinnedFingerprintNoLongerMatchesTemplate() {
        ContainerTemplateMapper templateMapper = mock(ContainerTemplateMapper.class);
        EnvironmentPortAllocationMapper portMapper = mock(EnvironmentPortAllocationMapper.class);
        EnvironmentCommandFactory factory = new EnvironmentCommandFactory(templateMapper, portMapper,
                new ObjectMapper().findAndRegisterModules());
        CompetitionEnvironmentRecord environment = competitionEnvironment();
        when(templateMapper.selectVersion("annotation-template", 3))
                .thenReturn(template("annotation-template", 3, "ANNOTATION", "/root/data"));
        when(templateMapper.selectVersion("editor-template", 5))
                .thenReturn(template("editor-template", 5, "EDITOR", "/home/student/data"));
        when(portMapper.selectBySlot("slot-2")).thenReturn(Arrays.asList(
                port("ANNOTATION", 8080, 8082), port("EDITOR", 9090, 9092)));
        environment.setAnnotationConfigFingerprint("changed-fingerprint");

        try {
            factory.createPayloadJson(environment, "operation-2");
            fail("expected pinned fingerprint mismatch");
        } catch (IllegalStateException expected) {
            assertEquals("环境固定模板指纹不匹配: ANNOTATION", expected.getMessage());
        }
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

    private CompetitionEnvironmentRecord competitionEnvironment() {
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setEnvironmentId("competition-environment-2");
        environment.setSlotId("slot-2");
        environment.setWorkspaceRelativePath("competition/slot-2");
        environment.setAnnotationTemplateId("annotation-template");
        environment.setAnnotationTemplateVersion(3);
        environment.setAnnotationConfigFingerprint("annotation-fingerprint");
        environment.setAnnotationContainerName("xkp-comp-11111111-s2-annotation");
        environment.setEditorTemplateId("editor-template");
        environment.setEditorTemplateVersion(5);
        environment.setEditorConfigFingerprint("editor-fingerprint");
        environment.setEditorContainerName("xkp-comp-11111111-s2-editor");
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
