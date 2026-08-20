package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.entity.User;
import com.match.environment.model.CompetitionSlotView;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.mapper.UserMapper;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import com.match.mode.service.ModeConflictException;
import com.match.mode.service.ProcessingAgentModeGuard;
import com.match.security.AdminAccessException;
import com.match.security.ParticipantModeGuard;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompetitionSlotBindingServiceTest {
    private static final String SLOT_ID = "22222222-2222-4222-8222-222222222222";
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ENVIRONMENT_ID = "33333333-3333-4333-8333-333333333333";
    private static final String ANNOTATION_FP = repeat('a');
    private static final String EDITOR_FP = repeat('b');
    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");

    private ProcessingEnvironmentSlotMapper slots;
    private CompetitionEnvironmentMapper environments;
    private ProcessingAgentModeMapper agentModes;
    private UserMapper users;
    private ProcessingAgentMapper agents;
    private EnvironmentPortAllocationMapper ports;
    private RoleGuard roleGuard;
    private ParticipantModeGuard modeGuard;
    private AgentAuditService audit;
    private CompetitionSlotBindingService service;

    @Before
    public void setUp() {
        slots = mock(ProcessingEnvironmentSlotMapper.class);
        environments = mock(CompetitionEnvironmentMapper.class);
        agentModes = mock(ProcessingAgentModeMapper.class);
        users = mock(UserMapper.class);
        agents = mock(ProcessingAgentMapper.class);
        ports = mock(EnvironmentPortAllocationMapper.class);
        roleGuard = mock(RoleGuard.class);
        modeGuard = mock(ParticipantModeGuard.class);
        audit = mock(AgentAuditService.class);
        service = new CompetitionSlotBindingService(slots, environments,
                new ProcessingAgentModeGuard(agentModes), users,
                agents, ports, roleGuard, modeGuard, audit, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void refusesBindingWhenCompetitionPairIsMissing() {
        stubBoundaryAndSlot(admin(), 21);
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(null);

        assertCode("COMPETITION_ENVIRONMENT_NOT_CREATED",
                () -> service.bind(SLOT_ID, 21, admin()));

        verify(slots, never()).bindIfUnbound(anyString(), anyInt(), any(LocalDateTime.class));
    }

    @Test
    public void refusesOneMissingComponent() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setEditorContainerName(null);
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_INCOMPLETE", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesRunningStoredComponent() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setAnnotationContainerState("RUNNING");
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_NOT_STOPPED", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesAbsentSuccessfulAgentVerification() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setLastVerifiedAt(null);
        environment.setLastComponentResultsJson(null);
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesOversizedAgentVerification() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        StringBuilder oversized = new StringBuilder(environment.getLastComponentResultsJson());
        while (oversized.length() <= 17 * 1024) {
            oversized.append(' ');
        }
        environment.setLastComponentResultsJson(oversized.toString());
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesObservedFingerprintMismatch() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setLastComponentResultsJson(result(ANNOTATION_FP, repeat('c'), "STOPPED", "STOPPED",
                environment.getAnnotationContainerName(), environment.getEditorContainerName()));
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_FINGERPRINT_MISMATCH",
                () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesObservedComponentIdentityOrNameMismatch() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setLastComponentResultsJson(result(ANNOTATION_FP, EDITOR_FP, "STOPPED", "STOPPED",
                environment.getAnnotationContainerName(), "other-editor"));
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_IDENTITY_MISMATCH",
                () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesDuplicateUserOnSameAgent() {
        stubReadyInfrastructure(readyEnvironment(), 21);
        when(slots.selectByAgentAndUserForUpdate(AGENT_ID, 21)).thenReturn(slot(21));

        assertCode("COMPETITION_USER_ALREADY_BOUND", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesAlreadyBoundSlot() {
        ProcessingEnvironmentSlotRecord occupied = slot(99);
        stubBoundary(admin());
        when(slots.selectForUpdate(SLOT_ID)).thenReturn(occupied);
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(readyEnvironment());
        User target = participant(21, true);
        when(users.selectById(21)).thenReturn(target);
        when(roleGuard.roleOf(target)).thenReturn(UserRole.USER);
        when(slots.selectByAgentAndUserForUpdate(AGENT_ID, 21)).thenReturn(null);

        assertCode("COMPETITION_SLOT_ALREADY_BOUND", () -> service.bind(SLOT_ID, 21, admin()));
        verify(agentModes).selectForUpdate(AGENT_ID);
        verify(environments).selectBySlotForUpdate(SLOT_ID);
        verify(users).selectById(21);
        verify(slots).selectByAgentAndUserForUpdate(AGENT_ID, 21);
    }

    @Test
    public void refusesActiveAgentTransition() {
        stubBoundaryAndSlot(admin(), 21);
        ProcessingAgentModeRecord mode = normalMode();
        mode.setActiveTransitionId("44444444-4444-4444-8444-444444444444");
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(mode);

        assertCode("AGENT_MODE_TRANSITION_ACTIVE", () -> service.bind(SLOT_ID, 21, admin()));
        verify(environments, never()).selectBySlotForUpdate(anyString());
    }

    @Test
    public void bindingChecksAdministrativeTrainingModeBeforeLockingSlot() {
        User actor = admin();
        stubBoundary(actor);
        org.mockito.Mockito.doThrow(new ModeConflictException("PLATFORM_MODE_NOT_TRAINING", "wrong mode"))
                .when(modeGuard).requireAdministrativeTrainingMode();

        assertCode("PLATFORM_MODE_NOT_TRAINING", () -> service.bind(SLOT_ID, 21, actor));

        verify(slots, never()).selectForUpdate(anyString());
    }

    @Test
    public void bindsOnlyCompleteStoppedExactPairInRequiredOrder() {
        User actor = admin();
        stubReadyInfrastructure(readyEnvironment(), 21);
        when(slots.bindIfUnbound(SLOT_ID, 21, utcNow())).thenReturn(1);

        CompetitionSlotView result = service.bind(SLOT_ID, 21, actor);

        assertEquals(Integer.valueOf(21), result.getUserId());
        assertEquals("READY", result.getReadinessCode());
        InOrder order = inOrder(roleGuard, modeGuard, slots, agentModes, environments, users, audit);
        order.verify(roleGuard).roleOf(actor);
        order.verify(modeGuard).requireAdministrativeTrainingMode();
        order.verify(slots).selectForUpdate(SLOT_ID);
        order.verify(agentModes).selectForUpdate(AGENT_ID);
        order.verify(environments).selectBySlotForUpdate(SLOT_ID);
        order.verify(users).selectById(21);
        order.verify(slots).selectByAgentAndUserForUpdate(AGENT_ID, 21);
        order.verify(slots).bindIfUnbound(SLOT_ID, 21, utcNow());
        order.verify(audit).recordSuccess("COMPETITION_SLOT_BOUND", 7, AGENT_ID, null);
    }

    @Test
    public void bindCasRaceFailsClosed() {
        stubReadyInfrastructure(readyEnvironment(), 21);
        when(slots.bindIfUnbound(SLOT_ID, 21, utcNow())).thenReturn(0);

        assertCode("COMPETITION_SLOT_BIND_CONFLICT", () -> service.bind(SLOT_ID, 21, admin()));
        verify(audit, never()).recordSuccess(anyString(), anyInt(), anyString(), any());
    }

    @Test
    public void unbindRequiresExactUserStoppedReadyPairAndCas() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        User actor = admin();
        stubBoundary(actor);
        when(slots.selectForUpdate(SLOT_ID)).thenReturn(slot(21));
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        when(slots.unbindIfBoundTo(SLOT_ID, 21, utcNow())).thenReturn(1);

        CompetitionSlotView result = service.unbind(SLOT_ID, 21, actor);

        assertNull(result.getUserId());
        verify(audit).recordSuccess("COMPETITION_SLOT_UNBOUND", 7, AGENT_ID, null);
    }

    @Test
    public void unbindCasRaceFailsClosed() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        stubBoundary(admin());
        when(slots.selectForUpdate(SLOT_ID)).thenReturn(slot(21));
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        when(slots.unbindIfBoundTo(SLOT_ID, 21, utcNow())).thenReturn(0);

        assertCode("COMPETITION_SLOT_UNBIND_CONFLICT", () -> service.unbind(SLOT_ID, 21, admin()));
    }

    @Test
    public void superAdminUserDisabledAndNullActorsCannotBind() {
        for (User actor : Arrays.asList(superAdmin(), participant(8, true), participant(9, false), null)) {
            try {
                service.bind(SLOT_ID, 21, actor);
                fail("only an enabled ADMIN actor may bind");
            } catch (AdminAccessException expected) {
                assertEquals("仅普通管理员可以绑定比赛槽位", expected.getMessage());
            }
        }
        verify(modeGuard, never()).requireAdministrativeTrainingMode();
    }

    @Test
    public void participantViewReturnsOnlyBoundedStatesAndRevealsUrlsOnlyWhenRunning() {
        User participant = participant(21, true);
        ProcessingEnvironmentSlotRecord bound = slot(21);
        CompetitionEnvironmentRecord environment = readyEnvironment();
        ProcessingAgentRecord agent = agent();
        when(slots.selectByUser(21)).thenReturn(Collections.singletonList(bound));
        when(environments.selectBySlot(SLOT_ID)).thenReturn(environment);
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                port("ANNOTATION", 18033), port("EDITOR", 18100),
                port("ANNOTATION", 8081), port("EDITOR", 9091)));
        when(roleGuard.roleOf(participant)).thenReturn(UserRole.USER);

        environment.setActualState("STARTING");
        CompetitionSlotView starting = service.currentForUser(participant);
        assertEquals("STARTING", starting.getReadiness());
        assertNull(starting.getAnnotationUrl());
        assertNull(starting.getEditorUrl());

        environment.setActualState("ERROR");
        assertEquals("DEGRADED", service.currentForUser(participant).getReadiness());

        environment.setActualState("RUNNING");
        environment.setAnnotationContainerState("RUNNING");
        environment.setEditorContainerState("RUNNING");
        CompetitionSlotView unverifiedRunning = service.currentForUser(participant);
        assertEquals("DEGRADED", unverifiedRunning.getReadiness());
        assertNull(unverifiedRunning.getAnnotationUrl());
        assertNull(unverifiedRunning.getEditorUrl());

        environment.setLastComponentResultsJson(result(ANNOTATION_FP, EDITOR_FP,
                "RUNNING", "RUNNING", environment.getAnnotationContainerName(),
                environment.getEditorContainerName()));
        agent.setPrimaryIp("evil.example/path");
        CompetitionSlotView unsafeEndpoint = service.currentForUser(participant);
        assertEquals("DEGRADED", unsafeEndpoint.getReadiness());
        assertNull(unsafeEndpoint.getAnnotationUrl());
        assertNull(unsafeEndpoint.getEditorUrl());

        agent.setPrimaryIp("10.0.0.8");
        assertEquals("RUNNING", service.currentForUser(participant).getReadiness());
        assertEquals("http://10.0.0.8:8081", service.currentForUser(participant).getAnnotationUrl());
        assertEquals("http://10.0.0.8:9091", service.currentForUser(participant).getEditorUrl());
    }

    @Test
    public void unboundParticipantIsSuccessfulAndContainsNoUrls() {
        User participant = participant(21, true);
        when(roleGuard.roleOf(participant)).thenReturn(UserRole.USER);
        when(slots.selectByUser(21)).thenReturn(Collections.emptyList());

        CompetitionSlotView result = service.currentForUser(participant);

        assertEquals("UNBOUND", result.getReadiness());
        assertNull(result.getAnnotationUrl());
        assertNull(result.getEditorUrl());
        verify(environments, never()).selectBySlot(anyString());
    }

    @Test
    public void administrativeListIncludesEmptySlotAndContainerDetailsWithStableCode() {
        User actor = admin();
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        ProcessingEnvironmentSlotRecord empty = slot(null);
        when(slots.selectAll()).thenReturn(Collections.singletonList(empty));
        when(environments.selectBySlot(SLOT_ID)).thenReturn(null);

        CompetitionSlotView missing = service.list(actor).get(0);
        assertEquals(SLOT_ID, missing.getSlotId());
        assertEquals("COMPETITION_ENVIRONMENT_NOT_CREATED", missing.getReadinessCode());

        CompetitionEnvironmentRecord environment = readyEnvironment();
        when(environments.selectBySlot(SLOT_ID)).thenReturn(environment);
        CompetitionSlotView ready = service.list(actor).get(0);
        assertEquals(environment.getAnnotationContainerName(), ready.getAnnotationContainerName());
        assertEquals(environment.getEditorContainerName(), ready.getEditorContainerName());
        assertEquals(ANNOTATION_FP, ready.getAnnotationConfigFingerprint());
        assertEquals("READY", ready.getReadinessCode());
    }

    private void stubReadyInfrastructure(CompetitionEnvironmentRecord environment, int userId) {
        stubBoundaryAndSlot(admin(), userId);
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        when(users.selectById(userId)).thenReturn(participant(userId, true));
        when(roleGuard.roleOf(users.selectById(userId))).thenReturn(UserRole.USER);
        when(slots.selectByAgentAndUserForUpdate(AGENT_ID, userId)).thenReturn(null);
    }

    private void stubBoundaryAndSlot(User actor, int userId) {
        stubBoundary(actor);
        when(slots.selectForUpdate(SLOT_ID)).thenReturn(slot(null));
    }

    private void stubBoundary(User actor) {
        when(roleGuard.roleOf(any(User.class))).thenAnswer(invocation -> {
            User value = invocation.getArgument(0);
            return UserRole.valueOf(value.getRole());
        });
    }

    private User admin() {
        return user(7, "ADMIN", true);
    }

    private User superAdmin() {
        return user(1, "SUPER_ADMIN", true);
    }

    private User participant(int id, boolean enabled) {
        return user(id, "USER", enabled);
    }

    private User user(int id, String role, boolean enabled) {
        User user = new User();
        user.setUserId(id);
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }

    private ProcessingEnvironmentSlotRecord slot(Integer userId) {
        ProcessingEnvironmentSlotRecord slot = new ProcessingEnvironmentSlotRecord();
        slot.setSlotId(SLOT_ID);
        slot.setAgentId(AGENT_ID);
        slot.setSlotNumber(1);
        slot.setUserId(userId);
        return slot;
    }

    private ProcessingAgentModeRecord normalMode() {
        ProcessingAgentModeRecord mode = new ProcessingAgentModeRecord();
        mode.setAgentId(AGENT_ID);
        mode.setDesiredMode("TRAINING");
        mode.setActualMode("NORMAL");
        return mode;
    }

    private CompetitionEnvironmentRecord readyEnvironment() {
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setEnvironmentId(ENVIRONMENT_ID);
        environment.setAgentId(AGENT_ID);
        environment.setSlotId(SLOT_ID);
        environment.setSlotNumber(1);
        environment.setAnnotationTemplateId("55555555-5555-4555-8555-555555555555");
        environment.setAnnotationTemplateVersion(2);
        environment.setEditorTemplateId("66666666-6666-4666-8666-666666666666");
        environment.setEditorTemplateVersion(3);
        environment.setWorkspaceRelativePath("competition/slot-1");
        environment.setDesiredState("STOPPED");
        environment.setActualState("STOPPED");
        environment.setAnnotationContainerName("xkp-comp-11111111-s1-annotation");
        environment.setAnnotationContainerState("STOPPED");
        environment.setAnnotationConfigFingerprint(ANNOTATION_FP);
        environment.setEditorContainerName("xkp-comp-11111111-s1-editor");
        environment.setEditorContainerState("STOPPED");
        environment.setEditorConfigFingerprint(EDITOR_FP);
        environment.setLastVerifiedAt(utcNow());
        environment.setLastComponentResultsJson(result(ANNOTATION_FP, EDITOR_FP, "STOPPED", "STOPPED",
                environment.getAnnotationContainerName(), environment.getEditorContainerName()));
        return environment;
    }

    private String result(String annotationFingerprint, String editorFingerprint,
                          String annotationState, String editorState,
                          String annotationName, String editorName) {
        return "{\"pair\":{\"annotation\":{\"componentType\":\"ANNOTATION\","
                + "\"containerName\":\"" + annotationName + "\",\"configFingerprint\":\""
                + annotationFingerprint + "\",\"state\":\"" + annotationState + "\"},"
                + "\"editor\":{\"componentType\":\"EDITOR\",\"containerName\":\""
                + editorName + "\",\"configFingerprint\":\"" + editorFingerprint
                + "\",\"state\":\"" + editorState + "\"}}}";
    }

    private ProcessingAgentRecord agent() {
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setPrimaryIp("10.0.0.8");
        agent.setEnabled(true);
        return agent;
    }

    private EnvironmentPortAllocationRecord port(String type, int hostPort) {
        EnvironmentPortAllocationRecord port = new EnvironmentPortAllocationRecord();
        port.setComponentType(type);
        port.setHostPort(hostPort);
        return port;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }

    private void assertCode(String expected, Runnable action) {
        try {
            action.run();
            fail("expected ModeConflictException");
        } catch (ModeConflictException exception) {
            assertEquals(expected, exception.getCode());
        }
    }

    private static String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
