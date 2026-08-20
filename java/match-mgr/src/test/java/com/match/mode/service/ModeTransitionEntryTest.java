package com.match.mode.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.model.ModeTransitionView;
import com.match.mode.persistence.ModeTrainingSnapshotMapper;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionEntryTest {
    private static final Instant NOW = Instant.parse("2026-08-21T06:07:08Z");
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TRAINING_A = "22222222-2222-4222-8222-222222222222";
    private static final String TRAINING_B = "33333333-3333-4333-8333-333333333333";
    private static final String COMPETITION = "44444444-4444-4444-8444-444444444444";
    private static final String SLOT_ID = "55555555-5555-4555-8555-555555555555";

    private ProcessingAgentMapper agents;
    private ProcessingAgentModeMapper modes;
    private ModeTransitionMapper transitions;
    private ModeTransitionStepMapper steps;
    private ModeTrainingSnapshotMapper snapshots;
    private TrainingEnvironmentMapper training;
    private CompetitionEnvironmentMapper competition;
    private ProcessingEnvironmentSlotMapper slots;
    private EnvironmentOperationMapper operations;
    private AgentCommandService commands;
    private EnvironmentCommandFactory commandFactory;
    private AgentAuditService audit;
    private ModeTransitionService service;
    private ProcessingAgentModeRecord mode;
    private ProcessingAgentRecord agent;
    private User admin;

    @Before
    public void setUp() {
        agents = mock(ProcessingAgentMapper.class);
        modes = mock(ProcessingAgentModeMapper.class);
        transitions = mock(ModeTransitionMapper.class);
        steps = mock(ModeTransitionStepMapper.class);
        snapshots = mock(ModeTrainingSnapshotMapper.class);
        training = mock(TrainingEnvironmentMapper.class);
        competition = mock(CompetitionEnvironmentMapper.class);
        slots = mock(ProcessingEnvironmentSlotMapper.class);
        operations = mock(EnvironmentOperationMapper.class);
        commands = mock(AgentCommandService.class);
        commandFactory = mock(EnvironmentCommandFactory.class);
        audit = mock(AgentAuditService.class);
        service = new ModeTransitionService(modes, transitions, steps, snapshots, agents,
                training, competition, slots, operations, commands, commandFactory, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));

        agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);
        mode = mode("TRAINING", "NORMAL");
        when(modes.selectForUpdate(AGENT_ID)).thenReturn(mode);
        when(transitions.selectActiveForUpdate(AGENT_ID)).thenReturn(null);
        admin = new User();
        admin.setUserId(9);
        admin.setEnabled(true);
        admin.setRole("ADMIN");
        when(training.selectByAgentForUpdate(AGENT_ID)).thenReturn(Arrays.asList(
                environment(TRAINING_A, "RUNNING"), environment(TRAINING_B, "RUNNING")));
        when(training.selectForUpdate(TRAINING_A)).thenReturn(environment(TRAINING_A, "RUNNING"));
        when(training.selectForUpdate(TRAINING_B)).thenReturn(environment(TRAINING_B, "RUNNING"));
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(Collections.singletonList(slot(2)));
        when(competition.selectByAgent(AGENT_ID)).thenReturn(Collections.singletonList(competition()));
        when(operations.selectActive(anyString())).thenReturn(null);
        when(commandFactory.createPayloadJson(any(TrainingEnvironmentRecord.class), anyString()))
                .thenReturn("{\"training\":true}");
        when(commandFactory.createPayloadJson(any(CompetitionEnvironmentRecord.class), anyString()))
                .thenReturn("{\"competition\":true}");
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("66666666-6666-4666-8666-666666666666");
        when(commands.requestEnvironmentCommand(any(ProcessingAgentRecord.class), anyString(),
                anyString(), eq(9), eq("ADMIN"), anyString())).thenReturn(command);
        when(transitions.insert(any(ModeTransitionRecord.class))).thenReturn(1);
    }

    @Test
    public void entrySnapshotsRunningTrainingAndStartsOnlyBoundCompetitionAfterStopPhase() {
        ModeTransitionView result = service.planEntry(AGENT_ID, admin);

        assertNotNull(result);
        assertEquals("COMPETITION", result.getTargetMode());
        assertEquals(3, result.getSteps().size());
        assertEquals("STOP_TRAINING_ENVIRONMENT", result.getSteps().get(0).getActionType());
        assertEquals("START_COMPETITION_ENVIRONMENT", result.getSteps().get(2).getActionType());
        verify(snapshots, org.mockito.Mockito.times(2)).insert(any());
        verify(commands, org.mockito.Mockito.times(2)).requestEnvironmentCommand(
                any(ProcessingAgentRecord.class), anyString(), anyString(), eq(9), eq("ADMIN"), anyString());
        assertFalse(result.getSteps().get(2).getPhaseNumber() == 1);
    }

    @Test
    public void duplicateEntryReturnsTheActiveSameTargetWithoutNewSteps() {
        ModeTransitionRecord existing = transition("COMPETITION", "RUNNING");
        existing.setTransitionId("77777777-7777-4777-8777-777777777777");
        when(transitions.selectActiveForUpdate(AGENT_ID)).thenReturn(existing);

        ModeTransitionView result = service.planEntry(AGENT_ID, admin);

        assertEquals(existing.getTransitionId(), result.getTransitionId());
        verify(transitions, never()).insert(any(ModeTransitionRecord.class));
        verify(commands, never()).requestEnvironmentCommand(any(), anyString(), anyString(),
                anyInt(), anyString(), anyString());
    }

    @Test
    public void offlineAgentGetsDurableDegradedTransitionWithoutDispatch() {
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(301), ZoneOffset.UTC));

        ModeTransitionView result = service.planEntry(AGENT_ID, admin);

        assertEquals("DEGRADED", result.getState());
        assertEquals("AGENT_OFFLINE", result.getFailureSummary());
        verify(commands, never()).requestEnvironmentCommand(any(), anyString(), anyString(),
                anyInt(), anyString(), anyString());
    }

    @Test
    public void activeEnvironmentOperationGetsDurableDegradedTransition() {
        EnvironmentOperationRecord active = new EnvironmentOperationRecord();
        active.setOperationId("88888888-8888-4888-8888-888888888888");
        when(operations.selectActive(TRAINING_A)).thenReturn(active);

        ModeTransitionView result = service.planEntry(AGENT_ID, admin);

        assertEquals("DEGRADED", result.getState());
        assertEquals("ENVIRONMENT_OPERATION_ACTIVE", result.getFailureSummary());
        verify(commands, never()).requestEnvironmentCommand(any(), anyString(), anyString(),
                anyInt(), anyString(), anyString());
    }

    @Test
    public void zeroTrainingAndZeroBoundCompetitionCompletesImmediately() {
        when(training.selectByAgentForUpdate(AGENT_ID)).thenReturn(Collections.<TrainingEnvironmentRecord>emptyList());
        when(slots.selectByAgentForUpdate(AGENT_ID)).thenReturn(Collections.<ProcessingEnvironmentSlotRecord>emptyList());
        when(competition.selectByAgent(AGENT_ID)).thenReturn(Collections.<CompetitionEnvironmentRecord>emptyList());

        ModeTransitionView result = service.planEntry(AGENT_ID, admin);

        assertEquals("SUCCEEDED", result.getState());
        verify(transitions).updateTerminal(anyString(), eq("SUCCEEDED"), eq(null), any(LocalDateTime.class));
        verify(commands, never()).requestEnvironmentCommand(any(), anyString(), anyString(),
                anyInt(), anyString(), anyString());
    }

    @Test
    public void failedStopBarrierNeverDispatchesCompetitionStart() {
        ModeTransitionRecord existing = transition("COMPETITION", "DEGRADED");
        existing.setTransitionId("99999999-9999-4999-8999-999999999999");
        when(transitions.selectForUpdate(existing.getTransitionId())).thenReturn(existing);
        com.match.mode.persistence.ModeTransitionStepRecord failedStop = modeStep(
                existing.getTransitionId(), 1, "FAILED", "STOP_TRAINING_ENVIRONMENT");
        com.match.mode.persistence.ModeTransitionStepRecord pendingStart = modeStep(
                existing.getTransitionId(), 2, "PENDING", "START_COMPETITION_ENVIRONMENT");
        when(steps.selectByTransition(existing.getTransitionId())).thenReturn(Arrays.asList(failedStop, pendingStart));

        service.dispatchReadyPhase(existing.getTransitionId());

        verify(commands, never()).requestEnvironmentCommand(any(), anyString(), anyString(),
                anyInt(), anyString(), anyString());
    }

    @Test
    public void oppositeActiveTargetIsRejected() {
        ModeTransitionRecord existing = transition("TRAINING", "RUNNING");
        when(transitions.selectActiveForUpdate(AGENT_ID)).thenReturn(existing);
        try {
            service.planEntry(AGENT_ID, admin);
            fail("expected mode conflict");
        } catch (ModeConflictException expected) {
            assertEquals("AGENT_MODE_TRANSITION_CONFLICT", expected.getCode());
        }
    }

    private ProcessingAgentModeRecord mode(String desired, String actual) {
        ProcessingAgentModeRecord result = new ProcessingAgentModeRecord();
        result.setAgentId(AGENT_ID);
        result.setDesiredMode(desired);
        result.setActualMode(actual);
        result.setLockVersion(0L);
        return result;
    }

    private ModeTransitionRecord transition(String target, String state) {
        ModeTransitionRecord result = new ModeTransitionRecord();
        result.setAgentId(AGENT_ID);
        result.setSourceMode("TRAINING");
        result.setTargetMode(target);
        result.setState(state);
        return result;
    }

    private TrainingEnvironmentRecord environment(String id, String state) {
        TrainingEnvironmentRecord result = new TrainingEnvironmentRecord();
        result.setEnvironmentId(id);
        result.setAgentId(AGENT_ID);
        result.setActualState(state);
        result.setDesiredState(state);
        result.setCurrentOperationId(null);
        return result;
    }

    private ProcessingEnvironmentSlotRecord slot(int number) {
        ProcessingEnvironmentSlotRecord result = new ProcessingEnvironmentSlotRecord();
        result.setAgentId(AGENT_ID);
        result.setSlotId(SLOT_ID);
        result.setSlotNumber(number);
        result.setUserId(42);
        return result;
    }

    private CompetitionEnvironmentRecord competition() {
        CompetitionEnvironmentRecord result = new CompetitionEnvironmentRecord();
        result.setEnvironmentId(COMPETITION);
        result.setAgentId(AGENT_ID);
        result.setSlotId(SLOT_ID);
        result.setSlotNumber(2);
        result.setWorkspaceRelativePath("competition/slot-2");
        result.setAnnotationTemplateId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        result.setAnnotationTemplateVersion(1);
        result.setEditorTemplateId("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        result.setEditorTemplateVersion(1);
        result.setDesiredState("STOPPED");
        result.setActualState("STOPPED");
        result.setAnnotationContainerName("xkp-comp-11111111-s2-annotation");
        result.setEditorContainerName("xkp-comp-11111111-s2-editor");
        result.setAnnotationContainerState("STOPPED");
        result.setEditorContainerState("STOPPED");
        result.setAnnotationConfigFingerprint("a");
        result.setEditorConfigFingerprint("b");
        result.setLastVerifiedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        result.setLastComponentResultsJson("{\"pair\":{\"annotation\":{\"componentType\":\"ANNOTATION\","
                + "\"containerName\":\"xkp-comp-11111111-s2-annotation\","
                + "\"configFingerprint\":\"a\",\"state\":\"STOPPED\"},"
                + "\"editor\":{\"componentType\":\"EDITOR\","
                + "\"containerName\":\"xkp-comp-11111111-s2-editor\","
                + "\"configFingerprint\":\"b\",\"state\":\"STOPPED\"}}}");
        return result;
    }

    private com.match.mode.persistence.ModeTransitionStepRecord modeStep(String transitionId,
                                                                           int phase,
                                                                           String state,
                                                                           String action) {
        com.match.mode.persistence.ModeTransitionStepRecord result =
                new com.match.mode.persistence.ModeTransitionStepRecord();
        result.setStepId(UUID.randomUUID().toString());
        result.setTransitionId(transitionId);
        result.setPhaseNumber(phase);
        result.setStepOrdinal(1);
        result.setEnvironmentKind(phase == 1 ? "TRAINING" : "COMPETITION");
        result.setEnvironmentId(phase == 1 ? TRAINING_A : COMPETITION);
        result.setActionType(action);
        result.setState(state);
        return result;
    }
}
