package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.guard.LicenseGuard;
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrainingEnvironmentServiceTest {
    @Test public void independentWorkspacesAreIsolatedByEnvironment() {
        request.setCourseId(null); request.setEnvironmentName("Independent A");
        service.create(request, 9, "SUPER_ADMIN");
        request.setEnvironmentName("Independent B");
        service.create(request, 9, "SUPER_ADMIN");
        ArgumentCaptor<TrainingEnvironmentRecord> rows = ArgumentCaptor.forClass(TrainingEnvironmentRecord.class);
        verify(environmentMapper, times(2)).insert(rows.capture());
        assertNotEquals(rows.getAllValues().get(0).getWorkspaceRelativePath(), rows.getAllValues().get(1).getWorkspaceRelativePath());
    }
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ProcessingAgentMapper agentMapper;
    private ProcessingEnvironmentSlotMapper slotMapper;
    private ContainerTemplateMapper templateMapper;
    private TrainingEnvironmentMapper environmentMapper;
    private EnvironmentPortAllocationMapper portMapper;
    private EnvironmentOperationMapper operationMapper;
    private AgentCommandService commandService;
    private LicenseGuard licenseGuard;
    private TrainingEnvironmentService service;
    private CreateTrainingEnvironmentRequest request;
    private ProcessingAgentRecord agent;

    @Before
    public void setUp() {
        agentMapper = mock(ProcessingAgentMapper.class);
        slotMapper = mock(ProcessingEnvironmentSlotMapper.class);
        templateMapper = mock(ContainerTemplateMapper.class);
        environmentMapper = mock(TrainingEnvironmentMapper.class);
        portMapper = mock(EnvironmentPortAllocationMapper.class);
        operationMapper = mock(EnvironmentOperationMapper.class);
        commandService = mock(AgentCommandService.class);
        licenseGuard = mock(LicenseGuard.class);
        service = new TrainingEnvironmentService(agentMapper, slotMapper, templateMapper,
                environmentMapper, portMapper, operationMapper, commandService, licenseGuard,
                objectMapper, Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC));

        agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setDisplayName("xt-test");
        agent.setEnabled(true);
        when(agentMapper.selectForManagement(AGENT_ID)).thenReturn(agent);
        when(templateMapper.selectVersion("annotation-template", 3))
                .thenReturn(template("annotation-template", 3, "ANNOTATION",
                        "sysbox-runc", "/root/data", "[{\"containerPort\":8080,\"protocol\":\"tcp\"}]"));
        when(templateMapper.selectVersion("editor-template", 5))
                .thenReturn(template("editor-template", 5, "EDITOR", "nvidia",
                        "/home/student/data", "[{\"containerPort\":9090,\"protocol\":\"tcp\"},"
                                + "{\"containerPort\":8887,\"protocol\":\"tcp\"},"
                                + "{\"containerPort\":5000,\"protocol\":\"tcp\"}]"));
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("22222222-2222-4222-8222-222222222222");
        when(commandService.requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("CREATE_TRAINING_ENVIRONMENT"), any(String.class), eq(9),
                eq("SUPER_ADMIN"), any(String.class))).thenReturn(command);

        request = new CreateTrainingEnvironmentRequest();
        request.setUserId(21);
        request.setCourseId("course-31");
        request.setAgentId(AGENT_ID);
        request.setSlotNumber(1);
        request.setAnnotationTemplateId("annotation-template");
        request.setAnnotationTemplateVersion(3);
        request.setEditorTemplateId("editor-template");
        request.setEditorTemplateVersion(5);
    }

    @Test
    public void createsPinnedPairWithOneSharedWorkspaceAndReservedPorts() throws Exception {
        service.create(request, 9, "SUPER_ADMIN");

        ArgumentCaptor<TrainingEnvironmentRecord> environment =
                ArgumentCaptor.forClass(TrainingEnvironmentRecord.class);
        verify(environmentMapper).insert(environment.capture());
        assertEquals("training/21/course-31", environment.getValue().getWorkspaceRelativePath());
        assertEquals(Integer.valueOf(3), environment.getValue().getAnnotationTemplateVersion());
        assertEquals(Integer.valueOf(5), environment.getValue().getEditorTemplateVersion());
        assertNotEquals(environment.getValue().getAnnotationContainerName(),
                environment.getValue().getEditorContainerName());
        verify(portMapper, times(4)).insert(any(EnvironmentPortAllocationRecord.class));

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(commandService).requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("CREATE_TRAINING_ENVIRONMENT"), payloadJson.capture(), eq(9),
                eq("SUPER_ADMIN"), any(String.class));
        EnvironmentCommandPayload payload = objectMapper.readValue(payloadJson.getValue(),
                EnvironmentCommandPayload.class);
        assertEquals("training/21/course-31", payload.getWorkspaceRelativePath());
        assertEquals(2, payload.getComponents().size());
        assertEquals("/root/data", payload.getComponents().get(0).getMountTarget());
        assertEquals("/home/student/data", payload.getComponents().get(1).getMountTarget());
    }

    @Test
    public void rejectsASecondSlotForTheSameUserOnOneAgent() {
        ProcessingEnvironmentSlotRecord assigned = slot(1, 21);
        when(slotMapper.selectByAgentAndUserForUpdate(AGENT_ID, 21)).thenReturn(assigned);
        request.setSlotNumber(2);

        expectIllegalArgument(() -> service.create(request, 9, "SUPER_ADMIN"), "槽位");

        verify(slotMapper, never()).insert(any(ProcessingEnvironmentSlotRecord.class));
        verify(environmentMapper, never()).insert(any(TrainingEnvironmentRecord.class));
    }

    @Test
    public void reusesExactPortReservationsWhenTheSlotGetsAnotherCourse() {
        ProcessingEnvironmentSlotRecord assigned = slot(1, 21);
        when(slotMapper.selectByAgentAndUserForUpdate(AGENT_ID, 21)).thenReturn(assigned);
        when(slotMapper.selectByAgentAndNumberForUpdate(AGENT_ID, 1)).thenReturn(assigned);
        when(portMapper.selectAgentPortForUpdate(eq(AGENT_ID), any(Integer.class), eq("tcp")))
                .thenAnswer(invocation -> allocation(assigned.getSlotId(), invocation.getArgument(1)));
        request.setCourseId("course-32");

        service.create(request, 9, "SUPER_ADMIN");

        verify(portMapper, never()).insert(any(EnvironmentPortAllocationRecord.class));
        verify(environmentMapper).insert(any(TrainingEnvironmentRecord.class));
    }

    @Test
    public void portConflictDoesNotCreateEnvironmentOperationOrCommand() {
        ProcessingEnvironmentSlotRecord assigned = slot(1, 21);
        when(slotMapper.selectByAgentAndUserForUpdate(AGENT_ID, 21)).thenReturn(assigned);
        EnvironmentPortAllocationRecord foreign = allocation("slot-4", 8081);
        when(portMapper.selectAgentPortForUpdate(AGENT_ID, 8081, "tcp")).thenReturn(foreign);

        expectIllegalArgument(() -> service.create(request, 9, "SUPER_ADMIN"), "端口");

        verify(environmentMapper, never()).insert(any(TrainingEnvironmentRecord.class));
        verify(operationMapper, never()).insert(any());
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void usesSelectedCompetitionPortsInTheCreateCommand() throws Exception {
        request.setEnvironmentType("COMPETITION");
        request.setAnnotationHostPort(8094);
        request.setEditorVscodeHostPort(9194);
        request.setEditorJupyterHostPort(9994);
        request.setEditorT100HostPort(5504);

        service.create(request, 9, "SUPER_ADMIN");

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(commandService).requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("CREATE_TRAINING_ENVIRONMENT"), payloadJson.capture(), eq(9),
                eq("SUPER_ADMIN"), any(String.class));
        EnvironmentCommandPayload payload = objectMapper.readValue(payloadJson.getValue(),
                EnvironmentCommandPayload.class);
        String command = payload.getComponents().get(1).getCommand().get(2);
        assertTrue(command.contains("--disable-update-check"));
        assertTrue(command.contains("until wget -q --no-check-certificate"));
        assertTrue(command.contains("--ServerApp.default_url=/lab"));
        assertEquals(Integer.valueOf(8094), payload.getComponents().get(0).getPorts().get(0).getHostPort());
        assertEquals(Integer.valueOf(9194), payload.getComponents().get(1).getPorts().get(0).getHostPort());
        assertEquals(Integer.valueOf(9994), payload.getComponents().get(1).getPorts().get(1).getHostPort());
        assertEquals(Integer.valueOf(5504), payload.getComponents().get(1).getPorts().get(2).getHostPort());
    }

    @Test
    public void rejectsTemplateWhenServerHasAnotherImageDigest() {
        ImageDeploymentMapper deployments = mock(ImageDeploymentMapper.class);
        ImageDeploymentRecord deployed = new ImageDeploymentRecord();
        deployed.setTargetDigest("sha256:another");
        when(deployments.selectLatestSucceeded(AGENT_ID, "ANNOTATION")).thenReturn(deployed);
        when(deployments.selectLatestSucceeded(AGENT_ID, "EDITOR")).thenReturn(deployed);
        service.setImageDeploymentMapper(deployments);

        expectIllegalArgument(() -> service.create(request, 9, "SUPER_ADMIN"), "xt-test 缺少镜像 xkp/annotation:v1");

        verify(environmentMapper, never()).insert(any(TrainingEnvironmentRecord.class));
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void actualAgentImageInventoryAllowsDirectDeploymentDespiteLegacyRecordMismatch() {
        agent.setLatestMetrics("{\"dockerAvailable\":true,\"images\":["
                + "{\"repository\":\"xkp/annotation\",\"tag\":\"v1\",\"id\":\"sha256:a\"},"
                + "{\"repository\":\"xkp/editor\",\"tag\":\"v1\",\"id\":\"sha256:b\"}]}");
        ImageDeploymentMapper deployments = mock(ImageDeploymentMapper.class);
        ImageDeploymentRecord legacy = new ImageDeploymentRecord();
        legacy.setTargetDigest("sha256:obsolete");
        when(deployments.selectLatestSucceeded(any(String.class), any(String.class))).thenReturn(legacy);
        service.setImageDeploymentMapper(deployments);

        service.create(request, 9, "SUPER_ADMIN");

        verify(environmentMapper).insert(any(TrainingEnvironmentRecord.class));
    }

    @Test
    public void legacySuccessfulDeploymentRemainsCompatibleWhenInventoryIsUnavailable() {
        ImageDeploymentMapper deployments = mock(ImageDeploymentMapper.class);
        ImageDeploymentRecord annotation = new ImageDeploymentRecord();
        annotation.setTargetDigest("xkp/annotation:v1");
        ImageDeploymentRecord editor = new ImageDeploymentRecord();
        editor.setTargetDigest("xkp/editor:v1");
        when(deployments.selectLatestSucceeded(AGENT_ID, "ANNOTATION")).thenReturn(annotation);
        when(deployments.selectLatestSucceeded(AGENT_ID, "EDITOR")).thenReturn(editor);
        service.setImageDeploymentMapper(deployments);

        service.create(request, 9, "SUPER_ADMIN");

        verify(environmentMapper).insert(any(TrainingEnvironmentRecord.class));
    }

    @Test
    public void overwrittenTagCannotChangePinnedTemplateImage() throws Exception {
        ContainerTemplateRecord template = templateMapper.selectVersion("annotation-template", 3);
        template.setImageId("sha256:original");
        agent.setLatestMetrics("{\"dockerAvailable\":true,\"images\":["
                + "{\"repository\":\"xkp/annotation\",\"tag\":\"v1\",\"id\":\"sha256:replacement\"},"
                + "{\"repository\":\"xkp/editor\",\"tag\":\"v1\",\"id\":\"sha256:editor\"}]}");
        expectIllegalArgument(() -> service.create(request, 9, "SUPER_ADMIN"), "服务器缺少模板固定的镜像");
        agent.setLatestMetrics("{\"dockerAvailable\":true,\"images\":["
                + "{\"repository\":\"\",\"tag\":\"\",\"id\":\"sha256:original\"},"
                + "{\"repository\":\"xkp/editor\",\"tag\":\"v1\",\"id\":\"sha256:editor\"}]}");
        service.create(request, 9, "SUPER_ADMIN");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(commandService).requestEnvironmentCommand(any(),eq("CREATE_TRAINING_ENVIRONMENT"),payload.capture(),any(),any(),any());
        assertEquals("sha256:original", objectMapper.readTree(payload.getValue()).path("components").get(0).path("imageReference").asText());
    }

    private ContainerTemplateRecord template(String id, int version, String componentType,
                                             String runtime, String mountTarget, String portsJson) {
        ContainerTemplateRecord template = new ContainerTemplateRecord();
        template.setTemplateId(id);
        template.setTemplateVersion(version);
        template.setComponentType(componentType);
        template.setEnabled(true);
        template.setImageReference("xkp/" + componentType.toLowerCase() + ":v1");
        template.setRuntimeName(runtime);
        template.setRestartPolicy("always");
        template.setMountTarget(mountTarget);
        template.setPortsJson(portsJson);
        template.setCpuLimitMillis(1000);
        template.setMemoryLimitBytes(1024L * 1024L * 1024L);
        template.setGpuEnabled("EDITOR".equals(componentType));
        template.setConfigFingerprint(componentType.toLowerCase() + "-fingerprint");
        return template;
    }

    private ProcessingEnvironmentSlotRecord slot(int number, int userId) {
        ProcessingEnvironmentSlotRecord slot = new ProcessingEnvironmentSlotRecord();
        slot.setSlotId("slot-" + number);
        slot.setAgentId(AGENT_ID);
        slot.setSlotNumber(number);
        slot.setUserId(userId);
        return slot;
    }

    private EnvironmentPortAllocationRecord allocation(String slotId, int hostPort) {
        EnvironmentPortAllocationRecord allocation = new EnvironmentPortAllocationRecord();
        allocation.setSlotId(slotId);
        allocation.setAgentId(AGENT_ID);
        allocation.setComponentType(hostPort == 8081 ? "ANNOTATION" : "EDITOR");
        allocation.setContainerPort(hostPort == 8081 ? 8080
                : hostPort == 9091 ? 9090 : hostPort == 8881 ? 8887 : 5000);
        allocation.setHostPort(hostPort);
        allocation.setProtocol("tcp");
        return allocation;
    }

    private void expectIllegalArgument(Runnable action, String messageFragment) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(messageFragment));
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
