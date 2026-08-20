package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.environment.model.CompetitionEnvironmentView;
import com.match.environment.model.CreateCompetitionEnvironmentRequest;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.licensing.guard.LicenseAccessException;
import com.match.licensing.guard.LicenseGuard;
import com.match.security.AdminAccessException;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CompetitionEnvironmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T06:07:08Z");
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SLOT_ID = "22222222-2222-4222-8222-222222222222";
    private static final String ANNOTATION_ID = "33333333-3333-4333-8333-333333333333";
    private static final String EDITOR_ID = "44444444-4444-4444-8444-444444444444";
    private static final String ANNOTATION_FINGERPRINT =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EDITOR_FINGERPRINT =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private ProcessingAgentMapper agents;
    private ProcessingEnvironmentSlotMapper slots;
    private ContainerTemplateMapper templates;
    private CompetitionEnvironmentMapper environments;
    private EnvironmentPortAllocationMapper ports;
    private EnvironmentOperationMapper operations;
    private AgentCommandService commands;
    private EnvironmentCommandFactory commandFactory;
    private LicenseGuard licenseGuard;
    private RoleGuard roleGuard;
    private CompetitionEnvironmentService service;
    private CreateCompetitionEnvironmentRequest request;
    private User superAdmin;

    @Before
    public void setUp() {
        agents = mock(ProcessingAgentMapper.class);
        slots = mock(ProcessingEnvironmentSlotMapper.class);
        templates = mock(ContainerTemplateMapper.class);
        environments = mock(CompetitionEnvironmentMapper.class);
        ports = mock(EnvironmentPortAllocationMapper.class);
        operations = mock(EnvironmentOperationMapper.class);
        commands = mock(AgentCommandService.class);
        commandFactory = mock(EnvironmentCommandFactory.class);
        licenseGuard = mock(LicenseGuard.class);
        roleGuard = mock(RoleGuard.class);
        service = new CompetitionEnvironmentService(agents, slots, templates, environments,
                ports, operations, commands, commandFactory, licenseGuard, roleGuard,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        superAdmin = actor(9, true);
        when(roleGuard.roleOf(superAdmin)).thenReturn(UserRole.SUPER_ADMIN);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);
        ProcessingEnvironmentSlotRecord slot = new ProcessingEnvironmentSlotRecord();
        slot.setSlotId(SLOT_ID);
        slot.setAgentId(AGENT_ID);
        slot.setSlotNumber(2);
        slot.setUserId(null);
        when(slots.selectByAgentAndNumberForUpdate(AGENT_ID, 2)).thenReturn(slot);
        when(templates.selectVersion(ANNOTATION_ID, 3)).thenReturn(template(ANNOTATION_ID, 3,
                "ANNOTATION", ANNOTATION_FINGERPRINT));
        when(templates.selectVersion(EDITOR_ID, 5)).thenReturn(template(EDITOR_ID, 5,
                "EDITOR", EDITOR_FINGERPRINT));
        when(ports.selectAgentPortForUpdate(eq(AGENT_ID), anyInt(), eq("tcp")))
                .thenAnswer(invocation -> allocation(invocation.getArgument(1)));
        when(commandFactory.createPayloadJson(any(CompetitionEnvironmentRecord.class), anyString()))
                .thenReturn("{\"payload\":true}");
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("55555555-5555-4555-8555-555555555555");
        when(commands.requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                anyString(), anyString(), eq(9), eq("SUPER_ADMIN"), anyString())).thenReturn(command);

        request = new CreateCompetitionEnvironmentRequest();
        request.setAgentId(AGENT_ID);
        request.setSlotNumber(2);
        request.setAnnotationTemplateId(ANNOTATION_ID);
        request.setAnnotationTemplateVersion(3);
        request.setEditorTemplateId(EDITOR_ID);
        request.setEditorTemplateVersion(5);
    }

    @Test
    public void createsUnboundCompetitionPairForExactSlotWithPinnedIdentity() {
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                allocation(8082), allocation(9092)));
        CompetitionEnvironmentView view = service.create(request, superAdmin);

        ArgumentCaptor<CompetitionEnvironmentRecord> saved =
                ArgumentCaptor.forClass(CompetitionEnvironmentRecord.class);
        verify(environments).insert(saved.capture());
        CompetitionEnvironmentRecord record = saved.getValue();
        assertEquals(AGENT_ID, record.getAgentId());
        assertEquals(SLOT_ID, record.getSlotId());
        assertEquals(Integer.valueOf(2), record.getSlotNumber());
        assertEquals("competition/slot-2", record.getWorkspaceRelativePath());
        assertEquals("xkp-comp-11111111-s2-annotation", record.getAnnotationContainerName());
        assertEquals("xkp-comp-11111111-s2-editor", record.getEditorContainerName());
        assertEquals(ANNOTATION_ID, record.getAnnotationTemplateId());
        assertEquals(Integer.valueOf(3), record.getAnnotationTemplateVersion());
        assertEquals(ANNOTATION_FINGERPRINT, record.getAnnotationConfigFingerprint());
        assertEquals(EDITOR_ID, record.getEditorTemplateId());
        assertEquals(Integer.valueOf(5), record.getEditorTemplateVersion());
        assertEquals(EDITOR_FINGERPRINT, record.getEditorConfigFingerprint());
        assertEquals("STOPPED", record.getDesiredState());
        assertEquals("CREATING", record.getActualState());
        assertEquals("UNKNOWN", record.getAnnotationContainerState());
        assertEquals("UNKNOWN", record.getEditorContainerState());

        ArgumentCaptor<EnvironmentOperationRecord> operation =
                ArgumentCaptor.forClass(EnvironmentOperationRecord.class);
        verify(operations).insert(operation.capture());
        assertEquals("CREATE", operation.getValue().getOperationType());
        verify(commands).requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("CREATE_COMPETITION_ENVIRONMENT"), eq("{\"payload\":true}"), eq(9),
                eq("SUPER_ADMIN"), eq(record.getEnvironmentId() + ":CREATE"));

        assertNull(view.getUserId());
        assertEquals("competition/slot-2", view.getWorkspaceRelativePath());
        assertEquals("STOPPED", view.getDesiredState());
        assertEquals("CREATING", view.getActualState());
        assertEquals("UNKNOWN", view.getAnnotationContainerState());
        assertEquals("UNKNOWN", view.getEditorContainerState());
        assertNull(view.getLastVerifiedAt());
        assertNull(view.getLastComponentResultsJson());
        assertEquals("xkp/annotation:v3", view.getAnnotationImageReference());
        assertEquals("xkp/editor:v5", view.getEditorImageReference());
        assertEquals(Integer.valueOf(8082), view.getAnnotationPorts().get(0).getHostPort());
        assertEquals(Integer.valueOf(9092), view.getEditorPorts().get(0).getHostPort());
        assertEquals("STARTING", view.getReadiness());
        assertEquals("COMPETITION_ENVIRONMENT_CREATING", view.getReadinessCode());
    }

    @Test
    public void listsCompetitionDiagnosticsWithBoundedBatchQueries() {
        CompetitionEnvironmentRecord environment = existingEnvironment();
        ProcessingEnvironmentSlotRecord slot = new ProcessingEnvironmentSlotRecord();
        slot.setSlotId(SLOT_ID);
        slot.setAgentId(AGENT_ID);
        slot.setSlotNumber(2);
        slot.setUserId(42);
        when(environments.selectAllEnvironments()).thenReturn(Collections.singletonList(environment));
        when(slots.selectAll()).thenReturn(Collections.singletonList(slot));
        when(templates.selectAllVersions()).thenReturn(Arrays.asList(
                template(ANNOTATION_ID, 3, "ANNOTATION", ANNOTATION_FINGERPRINT),
                template(EDITOR_ID, 5, "EDITOR", EDITOR_FINGERPRINT)));
        when(ports.selectBySlots(Collections.singletonList(SLOT_ID))).thenReturn(Arrays.asList(
                allocation(8082), allocation(9092)));
        when(operations.selectLatestByEnvironments(
                Collections.singletonList(environment.getEnvironmentId())))
                .thenReturn(Collections.emptyList());

        CompetitionEnvironmentView view = service.list(superAdmin).get(0);

        assertEquals(Integer.valueOf(42), view.getUserId());
        assertEquals("READY", view.getReadiness());
        assertEquals(Integer.valueOf(8082), view.getAnnotationPorts().get(0).getHostPort());
        verify(slots, never()).selectById(anyString());
        verify(templates, never()).selectVersion(anyString(), anyInt());
        verify(ports, never()).selectBySlot(anyString());
        verify(operations, never()).selectRecent(anyString(), anyInt());
    }

    @Test
    public void rejectsSecondCompetitionEnvironmentForTheSamePhysicalSlot() {
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(existingEnvironment());

        expect(IllegalArgumentException.class, () -> service.create(request, superAdmin), "槽位");

        verify(environments, never()).insert(any(CompetitionEnvironmentRecord.class));
        verifyZeroInteractions(operations, commands);
    }

    @Test
    public void rejectsInvalidAgentAndSlotIdentityBeforePersisting() {
        ProcessingEnvironmentSlotRecord wrong = new ProcessingEnvironmentSlotRecord();
        wrong.setSlotId(SLOT_ID);
        wrong.setAgentId("99999999-9999-4999-8999-999999999999");
        wrong.setSlotNumber(2);
        when(slots.selectByAgentAndNumberForUpdate(AGENT_ID, 2)).thenReturn(wrong);

        expect(IllegalArgumentException.class, () -> service.create(request, superAdmin), "槽位");

        verify(environments, never()).insert(any(CompetitionEnvironmentRecord.class));
        verifyZeroInteractions(operations, commands);
    }

    @Test
    public void rejectsTemplateVersionTypeOrFingerprintInsteadOfFallingForward() {
        ContainerTemplateRecord wrong = template(ANNOTATION_ID, 4, "ANNOTATION", "short");
        when(templates.selectVersion(ANNOTATION_ID, 3)).thenReturn(wrong);

        expect(IllegalArgumentException.class, () -> service.create(request, superAdmin), "模板");

        verify(environments, never()).insert(any(CompetitionEnvironmentRecord.class));
        verifyZeroInteractions(operations, commands);
    }

    @Test
    public void allocatesArbitraryEditorTemplatePortsByTheExistingSlotIndexContract() {
        ContainerTemplateRecord editor = template(EDITOR_ID, 5, "EDITOR", EDITOR_FINGERPRINT);
        editor.setPortsJson("[{\"containerPort\":7000,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":7001,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":7002,\"protocol\":\"tcp\"}]");
        when(templates.selectVersion(EDITOR_ID, 5)).thenReturn(editor);
        when(ports.selectAgentPortForUpdate(eq(AGENT_ID), anyInt(), eq("tcp")))
                .thenReturn(null);

        service.create(request, superAdmin);

        verify(ports).selectAgentPortForUpdate(AGENT_ID, 9092, "tcp");
        verify(ports).selectAgentPortForUpdate(AGENT_ID, 8882, "tcp");
        verify(ports).selectAgentPortForUpdate(AGENT_ID, 5002, "tcp");
    }

    @Test
    public void requiresAnEnabledRealSuperAdminBeforeLicenseOrInfrastructureAccess() {
        User disabled = actor(9, false);
        when(roleGuard.roleOf(disabled)).thenReturn(UserRole.SUPER_ADMIN);
        User admin = actor(10, true);
        when(roleGuard.roleOf(admin)).thenReturn(UserRole.ADMIN);

        expect(AdminAccessException.class, () -> service.create(request, null), "超级管理员");
        expect(AdminAccessException.class, () -> service.create(request, disabled), "超级管理员");
        expect(AdminAccessException.class, () -> service.create(request, admin), "超级管理员");

        verifyZeroInteractions(licenseGuard, agents, slots, templates, environments,
                ports, operations, commands);
    }

    @Test
    public void activeLicenseIsRequiredBeforeInfrastructureMutation() {
        doThrow(new LicenseAccessException("EXPIRED", "license expired"))
                .when(licenseGuard).requireActive();

        expect(LicenseAccessException.class, () -> service.create(request, superAdmin), "expired");

        verifyZeroInteractions(agents, slots, templates, environments, ports, operations, commands);
    }

    @Test
    public void restoreKeepsDesiredStateStoppedAndDispatchesCompetitionRestore() {
        CompetitionEnvironmentRecord existing = existingEnvironment();
        existing.setActualState("ERROR");
        existing.setLockVersion(4L);
        when(environments.selectForUpdate(existing.getEnvironmentId())).thenReturn(existing);
        when(environments.compareAndSetState(eq(existing.getEnvironmentId()), eq(4L),
                eq("STOPPED"), eq("RESTORING"), anyString(), eq(9),
                eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))).thenReturn(1);

        CompetitionEnvironmentView view = service.restore(existing.getEnvironmentId(), superAdmin);

        verify(commands).requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("RESTORE_COMPETITION_ENVIRONMENT"), eq("{\"payload\":true}"), eq(9),
                eq("SUPER_ADMIN"), eq(existing.getEnvironmentId() + ":RESTORE"));
        assertEquals("STOPPED", view.getDesiredState());
        assertEquals("RESTORING", view.getActualState());
    }

    @Test
    public void restoreAcceptsDisabledHistoricalPinnedTemplateVersions() {
        CompetitionEnvironmentRecord existing = existingEnvironment();
        existing.setActualState("ERROR");
        existing.setLockVersion(4L);
        ContainerTemplateRecord disabledAnnotation = template(ANNOTATION_ID, 3,
                "ANNOTATION", ANNOTATION_FINGERPRINT);
        disabledAnnotation.setEnabled(false);
        ContainerTemplateRecord disabledEditor = template(EDITOR_ID, 5,
                "EDITOR", EDITOR_FINGERPRINT);
        disabledEditor.setEnabled(false);
        when(templates.selectVersion(ANNOTATION_ID, 3)).thenReturn(disabledAnnotation);
        when(templates.selectVersion(EDITOR_ID, 5)).thenReturn(disabledEditor);
        when(environments.selectForUpdate(existing.getEnvironmentId())).thenReturn(existing);
        when(environments.compareAndSetState(eq(existing.getEnvironmentId()), eq(4L),
                eq("STOPPED"), eq("RESTORING"), anyString(), eq(9),
                eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))).thenReturn(1);

        CompetitionEnvironmentView view = service.restore(existing.getEnvironmentId(), superAdmin);

        assertEquals("RESTORING", view.getActualState());
        verify(commands).requestEnvironmentCommand(any(ProcessingAgentRecord.class),
                eq("RESTORE_COMPETITION_ENVIRONMENT"), eq("{\"payload\":true}"), eq(9),
                eq("SUPER_ADMIN"), eq(existing.getEnvironmentId() + ":RESTORE"));
    }

    @Test
    public void allocatesDistinctHostPortsForEveryValidTemplatePort() {
        ContainerTemplateRecord annotation = template(ANNOTATION_ID, 3,
                "ANNOTATION", ANNOTATION_FINGERPRINT);
        annotation.setPortsJson("[{\"containerPort\":8080,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":8081,\"protocol\":\"tcp\"}]");
        ContainerTemplateRecord editor = template(EDITOR_ID, 5, "EDITOR", EDITOR_FINGERPRINT);
        editor.setPortsJson("[{\"containerPort\":9090,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":8887,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":5000,\"protocol\":\"tcp\"},"
                + "{\"containerPort\":5001,\"protocol\":\"tcp\"}]");
        when(templates.selectVersion(ANNOTATION_ID, 3)).thenReturn(annotation);
        when(templates.selectVersion(EDITOR_ID, 5)).thenReturn(editor);
        when(ports.selectAgentPortForUpdate(eq(AGENT_ID), anyInt(), eq("tcp"))).thenReturn(null);

        service.create(request, superAdmin);

        ArgumentCaptor<EnvironmentPortAllocationRecord> saved =
                ArgumentCaptor.forClass(EnvironmentPortAllocationRecord.class);
        verify(ports, org.mockito.Mockito.times(6)).insert(saved.capture());
        Set<Integer> hostPorts = new HashSet<>();
        for (EnvironmentPortAllocationRecord allocation : saved.getAllValues()) {
            hostPorts.add(allocation.getHostPort());
        }
        assertEquals(6, hostPorts.size());
        assertTrue(hostPorts.containsAll(Arrays.asList(8082, 9092, 8882, 5002)));
    }

    private CompetitionEnvironmentRecord existingEnvironment() {
        CompetitionEnvironmentRecord record = new CompetitionEnvironmentRecord();
        record.setEnvironmentId("66666666-6666-4666-8666-666666666666");
        record.setAgentId(AGENT_ID);
        record.setSlotId(SLOT_ID);
        record.setSlotNumber(2);
        record.setAnnotationTemplateId(ANNOTATION_ID);
        record.setAnnotationTemplateVersion(3);
        record.setAnnotationConfigFingerprint(ANNOTATION_FINGERPRINT);
        record.setEditorTemplateId(EDITOR_ID);
        record.setEditorTemplateVersion(5);
        record.setEditorConfigFingerprint(EDITOR_FINGERPRINT);
        record.setWorkspaceRelativePath("competition/slot-2");
        record.setDesiredState("STOPPED");
        record.setActualState("STOPPED");
        record.setAnnotationContainerName("xkp-comp-11111111-s2-annotation");
        record.setAnnotationContainerState("STOPPED");
        record.setEditorContainerName("xkp-comp-11111111-s2-editor");
        record.setEditorContainerState("STOPPED");
        record.setLockVersion(0L);
        record.setCreatedBy(9);
        record.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        record.setUpdatedBy(9);
        record.setUpdatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return record;
    }

    private ContainerTemplateRecord template(String id, int version, String type, String fingerprint) {
        ContainerTemplateRecord template = new ContainerTemplateRecord();
        template.setTemplateId(id);
        template.setTemplateVersion(version);
        template.setComponentType(type);
        template.setEnabled(true);
        template.setImageReference("xkp/" + type.toLowerCase() + ":v" + version);
        template.setRuntimeName("EDITOR".equals(type) ? "nvidia" : "sysbox-runc");
        template.setRestartPolicy("always");
        template.setPortsJson("[{\"containerPort\":"
                + ("ANNOTATION".equals(type) ? 8080 : 9090) + ",\"protocol\":\"tcp\"}]");
        template.setMountTarget("ANNOTATION".equals(type) ? "/root/data" : "/home/student/data");
        template.setGpuEnabled("EDITOR".equals(type));
        template.setConfigFingerprint(fingerprint);
        return template;
    }

    private EnvironmentPortAllocationRecord allocation(int hostPort) {
        EnvironmentPortAllocationRecord allocation = new EnvironmentPortAllocationRecord();
        allocation.setSlotId(SLOT_ID);
        allocation.setAgentId(AGENT_ID);
        allocation.setComponentType(hostPort == 8082 ? "ANNOTATION" : "EDITOR");
        allocation.setContainerPort(hostPort == 8082 ? 8080 : 9090);
        allocation.setHostPort(hostPort);
        allocation.setProtocol("tcp");
        return allocation;
    }

    private User actor(int id, boolean enabled) {
        User user = new User();
        user.setUserId(id);
        user.setEnabled(enabled);
        return user;
    }

    private void expect(Class<? extends RuntimeException> type, Runnable action, String message) {
        try {
            action.run();
            fail("expected " + type.getSimpleName());
        } catch (RuntimeException exception) {
            assertTrue(type.isInstance(exception));
            assertTrue(exception.getMessage().contains(message));
        }
    }
}
