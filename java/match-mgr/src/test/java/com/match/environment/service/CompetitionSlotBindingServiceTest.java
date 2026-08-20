package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.entity.User;
import com.match.environment.model.CompetitionSlotView;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
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
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
    private ContainerTemplateMapper templates;
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
        templates = mock(ContainerTemplateMapper.class);
        users = mock(UserMapper.class);
        agents = mock(ProcessingAgentMapper.class);
        ports = mock(EnvironmentPortAllocationMapper.class);
        roleGuard = mock(RoleGuard.class);
        modeGuard = mock(ParticipantModeGuard.class);
        audit = mock(AgentAuditService.class);
        service = new CompetitionSlotBindingService(slots, environments,
                new ProcessingAgentModeGuard(agentModes), templates, users,
                agents, ports, roleGuard, modeGuard, audit, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void slotMapperLocksWholeAgentSetInStableOrder() throws Exception {
        Method method = ProcessingEnvironmentSlotMapper.class.getMethod(
                "selectByAgentForUpdate", String.class);
        Select select = method.getAnnotation(Select.class);

        assertTrue(select.value()[0].contains("WHERE agent_id = #{agentId}"));
        assertTrue(select.value()[0].contains("ORDER BY slot_number FOR UPDATE"));
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
    public void refusesUnknownOrDuplicateVerifiedPairMembers() {
        CompetitionEnvironmentRecord unknown = readyEnvironment();
        unknown.setLastComponentResultsJson("{\"pair\":{\"annotation\":"
                + componentJson("ANNOTATION", unknown.getAnnotationContainerName(), ANNOTATION_FP, "STOPPED")
                + ",\"editor\":" + componentJson("EDITOR", unknown.getEditorContainerName(), EDITOR_FP, "STOPPED")
                + ",\"sidecar\":{\"state\":\"STOPPED\"}}}");
        stubReadyInfrastructure(unknown, 21);
        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));

        CompetitionEnvironmentRecord nonObject = readyEnvironment();
        nonObject.setLastComponentResultsJson("{\"pair\":[]}");
        stubReadyInfrastructure(nonObject, 21);
        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));

        CompetitionEnvironmentRecord duplicate = readyEnvironment();
        String annotation = componentJson("ANNOTATION", duplicate.getAnnotationContainerName(),
                ANNOTATION_FP, "STOPPED");
        String editor = componentJson("EDITOR", duplicate.getEditorContainerName(),
                EDITOR_FP, "STOPPED");
        duplicate.setLastComponentResultsJson("{\"pair\":{\"annotation\":" + annotation
                + ",\"annotation\":" + annotation + ",\"editor\":" + editor + "}}");
        stubReadyInfrastructure(duplicate, 21);
        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesTrailingJsonAfterVerifiedPair() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setLastComponentResultsJson(environment.getLastComponentResultsJson() + " {}");
        stubReadyInfrastructure(environment, 21);

        assertCode("COMPETITION_PAIR_NOT_VERIFIED", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesIncompleteCreationMetadataAndMissingPinnedPorts() {
        CompetitionEnvironmentRecord missingMetadata = readyEnvironment();
        missingMetadata.setAnnotationTemplateId(null);
        stubReadyInfrastructure(missingMetadata, 21);
        assertCode("COMPETITION_PAIR_INCOMPLETE",
                () -> service.bind(SLOT_ID, 21, admin()));

        CompetitionEnvironmentRecord missingPort = readyEnvironment();
        stubReadyInfrastructure(missingPort, 21);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Collections.singletonList(
                allocation(SLOT_ID, "ANNOTATION", 8080, 8081)));
        assertCode("COMPETITION_PAIR_PORTS_INCOMPLETE",
                () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesDuplicateOrInvalidPinnedPortAllocations() {
        CompetitionEnvironmentRecord duplicate = readyEnvironment();
        stubReadyInfrastructure(duplicate, 21);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                allocation(SLOT_ID, "ANNOTATION", 8080, 8081),
                allocation(SLOT_ID, "ANNOTATION", 8080, 8082),
                allocation(SLOT_ID, "EDITOR", 9090, 9091)));
        assertCode("COMPETITION_PAIR_PORTS_INVALID", () -> service.bind(SLOT_ID, 21, admin()));

        CompetitionEnvironmentRecord invalid = readyEnvironment();
        stubReadyInfrastructure(invalid, 21);
        EnvironmentPortAllocationRecord bad = allocation(SLOT_ID, "ANNOTATION", 0, 8081);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(bad,
                allocation(SLOT_ID, "EDITOR", 9090, 9091)));
        assertCode("COMPETITION_PAIR_PORTS_INVALID", () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void refusesBindingWhenPinnedEndpointsDoNotUseFixedSlotHostPorts() {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        stubReadyInfrastructure(environment, 21);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                allocation(SLOT_ID, "ANNOTATION", 8080, 18081),
                allocation(SLOT_ID, "EDITOR", 9090, 19091),
                allocation(SLOT_ID, "ANNOTATION", 18000, 8081),
                allocation(SLOT_ID, "EDITOR", 19000, 9091)));

        assertCode("COMPETITION_PAIR_PORTS_INCOMPLETE",
                () -> service.bind(SLOT_ID, 21, admin()));
    }

    @Test
    public void mapsOnlyUniqueConstraintAndDeadlockRacesToStableBindConflict() {
        stubReadyInfrastructure(readyEnvironment(), 21);
        doThrow(new DuplicateKeyException("unique agent user"))
                .when(slots).bindIfUnbound(SLOT_ID, 21, utcNow());
        assertCode("COMPETITION_SLOT_BIND_CONFLICT", () -> service.bind(SLOT_ID, 21, admin()));

        doThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .when(slots).bindIfUnbound(SLOT_ID, 21, utcNow());
        assertCode("COMPETITION_SLOT_BIND_CONFLICT", () -> service.bind(SLOT_ID, 21, admin()));

        DataAccessResourceFailureException databaseDown =
                new DataAccessResourceFailureException("database down");
        doThrow(databaseDown).when(slots).bindIfUnbound(SLOT_ID, 21, utcNow());
        try {
            service.bind(SLOT_ID, 21, admin());
            fail("non-concurrency database errors must propagate");
        } catch (DataAccessResourceFailureException expected) {
            assertEquals(databaseDown, expected);
        }
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
        when(slots.selectById(SLOT_ID)).thenReturn(occupied);
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(lockedAgentSlots(99));
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        CompetitionEnvironmentRecord environment = readyEnvironment();
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        stubTemplates(environment);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(allocations(SLOT_ID));
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

        verify(slots, never()).selectById(anyString());
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
        order.verify(slots).selectById(SLOT_ID);
        order.verify(slots).selectByAgentForUpdate(AGENT_ID);
        order.verify(agentModes).selectForUpdate(AGENT_ID);
        order.verify(environments).selectBySlotForUpdate(SLOT_ID);
        order.verify(users).selectById(21);
        order.verify(slots).selectByAgentAndUserForUpdate(AGENT_ID, 21);
        order.verify(slots).bindIfUnbound(SLOT_ID, 21, utcNow());
        order.verify(audit).recordSuccess("COMPETITION_SLOT_BOUND", 7, AGENT_ID, null);
    }

    @Test
    public void refusesExistingSlotWhenAgentHasOnlyThreeLockedSlots() {
        stubReadyInfrastructure(readyEnvironment(), 21);
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(Arrays.asList(
                slot(null), slotRecord(2), slotRecord(4)));
        when(slots.bindIfUnbound(SLOT_ID, 21, utcNow())).thenReturn(1);

        assertCode("COMPETITION_AGENT_SLOTS_NOT_READY",
                () -> service.bind(SLOT_ID, 21, admin()));

        verify(slots, never()).bindIfUnbound(anyString(), anyInt(), any(LocalDateTime.class));
    }

    @Test
    public void refusesExistingSlotWhenAgentHasIllegalFifthSlot() {
        stubReadyInfrastructure(readyEnvironment(), 21);
        ProcessingEnvironmentSlotRecord fifth = slotRecord(4);
        fifth.setSlotId("99999999-9999-4999-8999-999999999999");
        fifth.setSlotNumber(5);
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(Arrays.asList(
                slot(null), slotRecord(2), slotRecord(3), slotRecord(4), fifth));
        when(slots.bindIfUnbound(SLOT_ID, 21, utcNow())).thenReturn(1);

        assertCode("COMPETITION_AGENT_SLOTS_NOT_READY",
                () -> service.bind(SLOT_ID, 21, admin()));

        verify(slots, never()).bindIfUnbound(anyString(), anyInt(), any(LocalDateTime.class));
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
        when(slots.selectById(SLOT_ID)).thenReturn(slot(21));
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(lockedAgentSlots(21));
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        stubTemplates(environment);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(allocations(SLOT_ID));
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
        when(slots.selectById(SLOT_ID)).thenReturn(slot(21));
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(lockedAgentSlots(21));
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        stubTemplates(environment);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(allocations(SLOT_ID));
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
                allocation(SLOT_ID, "ANNOTATION", 18000, 18033),
                allocation(SLOT_ID, "EDITOR", 18001, 18100),
                allocation(SLOT_ID, "ANNOTATION", 8080, 8081),
                allocation(SLOT_ID, "EDITOR", 9090, 9091)));
        stubTemplates(environment);
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
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Collections.singletonList(
                allocation(SLOT_ID, "EDITOR", 9090, 9091)));
        CompetitionSlotView missingPinnedPort = service.currentForUser(participant);
        assertEquals("DEGRADED", missingPinnedPort.getReadiness());
        assertEquals("COMPETITION_PAIR_PORTS_INCOMPLETE", missingPinnedPort.getReadinessCode());
        assertNull(missingPinnedPort.getAnnotationUrl());
        assertNull(missingPinnedPort.getEditorUrl());

        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                allocation(SLOT_ID, "ANNOTATION", 18000, 18033),
                allocation(SLOT_ID, "EDITOR", 18001, 18100),
                allocation(SLOT_ID, "ANNOTATION", 8080, 8081),
                allocation(SLOT_ID, "EDITOR", 9090, 9091)));
        agent.setPrimaryIp("evil.example/path");
        CompetitionSlotView unsafeEndpoint = service.currentForUser(participant);
        assertEquals("DEGRADED", unsafeEndpoint.getReadiness());
        assertNull(unsafeEndpoint.getAnnotationUrl());
        assertNull(unsafeEndpoint.getEditorUrl());

        agent.setPrimaryIp("+10.0.0.8");
        assertEquals("DEGRADED", service.currentForUser(participant).getReadiness());
        agent.setPrimaryIp("-1.0.0.8");
        assertEquals("DEGRADED", service.currentForUser(participant).getReadiness());

        agent.setPrimaryIp("10.0.0.8");
        assertEquals("RUNNING", service.currentForUser(participant).getReadiness());
        assertEquals("http://10.0.0.8:8081", service.currentForUser(participant).getAnnotationUrl());
        assertEquals("http://10.0.0.8:9091", service.currentForUser(participant).getEditorUrl());
    }

    @Test
    public void participantNeverReceivesNonFixedOrStaleEndpointUrls() {
        User participant = participant(21, true);
        ProcessingEnvironmentSlotRecord bound = slot(21);
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setActualState("RUNNING");
        environment.setAnnotationContainerState("RUNNING");
        environment.setEditorContainerState("RUNNING");
        environment.setLastComponentResultsJson(result(ANNOTATION_FP, EDITOR_FP,
                "RUNNING", "RUNNING", environment.getAnnotationContainerName(),
                environment.getEditorContainerName()));
        when(roleGuard.roleOf(participant)).thenReturn(UserRole.USER);
        when(slots.selectByUser(21)).thenReturn(Collections.singletonList(bound));
        when(environments.selectBySlot(SLOT_ID)).thenReturn(environment);
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent());
        stubTemplates(environment);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(Arrays.asList(
                allocation(SLOT_ID, "ANNOTATION", 8080, 18081),
                allocation(SLOT_ID, "EDITOR", 9090, 19091),
                allocation(SLOT_ID, "ANNOTATION", 18000, 8081),
                allocation(SLOT_ID, "EDITOR", 19000, 9091)));

        CompetitionSlotView result = service.currentForUser(participant);

        assertEquals("DEGRADED", result.getReadiness());
        assertEquals("COMPETITION_PAIR_PORTS_INCOMPLETE", result.getReadinessCode());
        assertNull(result.getAnnotationUrl());
        assertNull(result.getEditorUrl());
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
    public void administrativeListReturnsFourSortedSlotsWithCompleteReadinessDetails() {
        User actor = admin();
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        when(agents.selectVisibleAgents()).thenReturn(Collections.singletonList(agent()));
        List<ProcessingEnvironmentSlotRecord> records = Arrays.asList(
                slotRecord(4), slotRecord(2), slotRecord(1), slotRecord(3));
        when(slots.selectAll()).thenReturn(records);
        for (ProcessingEnvironmentSlotRecord record : records) {
            CompetitionEnvironmentRecord environment = readyEnvironment(record);
            when(environments.selectBySlot(record.getSlotId())).thenReturn(environment);
            when(ports.selectBySlot(record.getSlotId())).thenReturn(allocations(record.getSlotId()));
            stubTemplates(environment);
        }

        List<CompetitionSlotView> result = service.list(actor);

        assertEquals(4, result.size());
        for (int index = 0; index < 4; index++) {
            CompetitionSlotView view = result.get(index);
            assertEquals(Integer.valueOf(index + 1), view.getSlotNumber());
            assertEquals("slot " + view.getSlotNumber() + " code " + view.getReadinessCode(),
                    "READY", view.getReadiness());
            assertEquals("READY", view.getReadinessCode());
            assertEquals(ANNOTATION_FP, view.getAnnotationConfigFingerprint());
            assertEquals(1, view.getAnnotationPorts().size());
            assertEquals(1, view.getEditorPorts().size());
        }
    }

    @Test
    public void administrativeListMarksExistingRowsWhenFourSlotProvisioningIsIncomplete() {
        User actor = admin();
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        when(agents.selectVisibleAgents()).thenReturn(Collections.singletonList(agent()));
        when(slots.selectAll()).thenReturn(Arrays.asList(slotRecord(1), slotRecord(2), slotRecord(4)));

        List<CompetitionSlotView> result = service.list(actor);

        assertEquals(4, result.size());
        for (CompetitionSlotView view : result) {
            assertNull(view.getSlotId());
            assertNull(view.getEnvironmentId());
            assertNull(view.getUserId());
            assertEquals("DEGRADED", view.getReadiness());
            assertEquals("COMPETITION_AGENT_SLOTS_NOT_READY", view.getReadinessCode());
        }
    }

    @Test
    public void administrativeListIncludesFourPlaceholdersForVisibleAgentWithNoSlots() {
        User actor = admin();
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        when(agents.selectVisibleAgents()).thenReturn(Collections.singletonList(agent()));
        when(slots.selectAll()).thenReturn(Collections.emptyList());

        List<CompetitionSlotView> result = service.list(actor);

        assertEquals(4, result.size());
        for (int index = 0; index < 4; index++) {
            CompetitionSlotView view = result.get(index);
            assertEquals(AGENT_ID, view.getAgentId());
            assertEquals(Integer.valueOf(index + 1), view.getSlotNumber());
            assertNull(view.getSlotId());
            assertEquals("DEGRADED", view.getReadiness());
            assertEquals("COMPETITION_AGENT_SLOTS_NOT_READY", view.getReadinessCode());
        }
    }

    @Test
    public void administrativeListFailsClosedForDuplicateOrIllegalSlots() {
        User actor = admin();
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        when(agents.selectVisibleAgents()).thenReturn(Collections.singletonList(agent()));
        ProcessingEnvironmentSlotRecord duplicate = slotRecord(1);
        duplicate.setSlotId("77777777-7777-4777-8777-777777777777");
        ProcessingEnvironmentSlotRecord illegal = slotRecord(4);
        illegal.setSlotNumber(5);
        when(slots.selectAll()).thenReturn(Arrays.asList(slotRecord(1), duplicate,
                slotRecord(2), slotRecord(3), illegal));

        List<CompetitionSlotView> result = service.list(actor);

        assertEquals(4, result.size());
        for (CompetitionSlotView view : result) {
            assertNull(view.getSlotId());
            assertEquals("DEGRADED", view.getReadiness());
            assertEquals("COMPETITION_AGENT_SLOTS_NOT_READY", view.getReadinessCode());
        }
    }

    private void stubReadyInfrastructure(CompetitionEnvironmentRecord environment, int userId) {
        stubBoundaryAndSlot(admin(), userId);
        when(agentModes.selectForUpdate(AGENT_ID)).thenReturn(normalMode());
        when(environments.selectBySlotForUpdate(SLOT_ID)).thenReturn(environment);
        stubTemplates(environment);
        when(ports.selectBySlot(SLOT_ID)).thenReturn(allocations(SLOT_ID));
        when(users.selectById(userId)).thenReturn(participant(userId, true));
        when(roleGuard.roleOf(users.selectById(userId))).thenReturn(UserRole.USER);
        when(slots.selectByAgentAndUserForUpdate(AGENT_ID, userId)).thenReturn(null);
    }

    private void stubBoundaryAndSlot(User actor, int userId) {
        stubBoundary(actor);
        when(slots.selectForUpdate(SLOT_ID)).thenReturn(slot(null));
        when(slots.selectById(SLOT_ID)).thenReturn(slot(null));
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(lockedAgentSlots(null));
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

    private CompetitionEnvironmentRecord readyEnvironment(ProcessingEnvironmentSlotRecord slot) {
        CompetitionEnvironmentRecord environment = readyEnvironment();
        environment.setEnvironmentId("33333333-3333-4333-8333-33333333333" + slot.getSlotNumber());
        environment.setSlotId(slot.getSlotId());
        environment.setSlotNumber(slot.getSlotNumber());
        String prefix = "xkp-comp-11111111-s" + slot.getSlotNumber();
        environment.setWorkspaceRelativePath("competition/slot-" + slot.getSlotNumber());
        environment.setAnnotationContainerName(prefix + "-annotation");
        environment.setEditorContainerName(prefix + "-editor");
        environment.setLastComponentResultsJson(result(ANNOTATION_FP, EDITOR_FP,
                "STOPPED", "STOPPED", environment.getAnnotationContainerName(),
                environment.getEditorContainerName()));
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

    private String componentJson(String type, String name, String fingerprint, String state) {
        return "{\"componentType\":\"" + type + "\",\"containerName\":\"" + name
                + "\",\"configFingerprint\":\"" + fingerprint + "\",\"state\":\""
                + state + "\"}";
    }

    private ProcessingAgentRecord agent() {
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setPrimaryIp("10.0.0.8");
        agent.setEnabled(true);
        return agent;
    }

    private EnvironmentPortAllocationRecord allocation(String slotId, String type,
                                                        int containerPort, int hostPort) {
        EnvironmentPortAllocationRecord port = new EnvironmentPortAllocationRecord();
        port.setSlotId(slotId);
        port.setAgentId(AGENT_ID);
        port.setComponentType(type);
        port.setContainerPort(containerPort);
        port.setHostPort(hostPort);
        port.setProtocol("tcp");
        return port;
    }

    private List<EnvironmentPortAllocationRecord> allocations(String slotId) {
        return Arrays.asList(allocation(slotId, "ANNOTATION", 8080, 8080 + slotNumber(slotId)),
                allocation(slotId, "EDITOR", 9090, 9090 + slotNumber(slotId)));
    }

    private int slotNumber(String slotId) {
        return SLOT_ID.equals(slotId) ? 1 : Integer.parseInt(slotId.substring(slotId.length() - 1));
    }

    private ProcessingEnvironmentSlotRecord slotRecord(int number) {
        ProcessingEnvironmentSlotRecord slot = new ProcessingEnvironmentSlotRecord();
        slot.setSlotId("82222222-2222-4222-8222-22222222222" + number);
        slot.setAgentId(AGENT_ID);
        slot.setSlotNumber(number);
        return slot;
    }

    private List<ProcessingEnvironmentSlotRecord> lockedAgentSlots(Integer targetUserId) {
        return Arrays.asList(slot(targetUserId), slotRecord(2), slotRecord(3), slotRecord(4));
    }

    private void stubTemplates(CompetitionEnvironmentRecord environment) {
        when(templates.selectVersion(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion())).thenReturn(template(
                environment.getAnnotationTemplateId(), environment.getAnnotationTemplateVersion(),
                "ANNOTATION", ANNOTATION_FP, 8080));
        when(templates.selectVersion(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion())).thenReturn(template(
                environment.getEditorTemplateId(), environment.getEditorTemplateVersion(),
                "EDITOR", EDITOR_FP, 9090));
    }

    private ContainerTemplateRecord template(String id, int version, String type,
                                             String fingerprint, int containerPort) {
        ContainerTemplateRecord template = new ContainerTemplateRecord();
        template.setTemplateId(id);
        template.setTemplateVersion(version);
        template.setComponentType(type);
        template.setConfigFingerprint(fingerprint);
        template.setPortsJson("[{\"containerPort\":" + containerPort
                + ",\"protocol\":\"tcp\"}]");
        return template;
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
