package com.match.mode.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.model.ModeTransitionView;
import com.match.mode.persistence.ModeTrainingSnapshotMapper;
import com.match.mode.persistence.ModeTrainingSnapshotRecord;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModeTransitionExitTest {
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private static final String COMPETITION_ID = "44444444-4444-4444-8444-444444444444";
    private static final String TRAINING_ID = "22222222-2222-4222-8222-222222222222";

    private ModeTransitionMapper transitions;
    private ModeTransitionStepMapper steps;
    private ModeTrainingSnapshotMapper snapshots;
    private ProcessingAgentModeMapper modes;
    private ModeTransitionService service;
    private ProcessingAgentModeRecord mode;
    private User admin;

    @Before
    public void setUp() {
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        transitions = mock(ModeTransitionMapper.class);
        steps = mock(ModeTransitionStepMapper.class);
        snapshots = mock(ModeTrainingSnapshotMapper.class);
        modes = mock(ProcessingAgentModeMapper.class);
        TrainingEnvironmentMapper training = mock(TrainingEnvironmentMapper.class);
        CompetitionEnvironmentMapper competition = mock(CompetitionEnvironmentMapper.class);
        ProcessingEnvironmentSlotMapper slots = mock(ProcessingEnvironmentSlotMapper.class);
        EnvironmentOperationMapper operations = mock(EnvironmentOperationMapper.class);
        AgentCommandService commands = mock(AgentCommandService.class);
        EnvironmentCommandFactory factory = mock(EnvironmentCommandFactory.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        service = new ModeTransitionService(modes, transitions, steps, snapshots, agents,
                training, competition, slots, operations, commands, factory, audit,
                Clock.fixed(Instant.parse("2026-08-21T06:07:08Z"), ZoneOffset.UTC));

        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);

        mode = new ProcessingAgentModeRecord();
        mode.setAgentId(AGENT_ID);
        mode.setDesiredMode("COMPETITION");
        mode.setActualMode("COMPETITION");
        mode.setActiveTransitionId("entry-transition");
        when(modes.selectForUpdate(AGENT_ID)).thenReturn(mode);
        when(transitions.selectActiveForUpdate(AGENT_ID)).thenReturn(null);
        when(transitions.selectLatestCompetitionForAgent(AGENT_ID)).thenReturn(entryTransition());
        when(steps.selectByTransition("entry-transition")).thenReturn(Collections.singletonList(
                step("entry-transition", 2, "COMPETITION", COMPETITION_ID,
                        "START_COMPETITION_ENVIRONMENT", "SUCCEEDED")));
        ModeTrainingSnapshotRecord snapshot = new ModeTrainingSnapshotRecord();
        snapshot.setTransitionId("entry-transition");
        snapshot.setEnvironmentId(TRAINING_ID);
        when(snapshots.selectByTransition("entry-transition")).thenReturn(Collections.singletonList(snapshot));
        admin = new User();
        admin.setUserId(9);
        admin.setEnabled(true);
        admin.setRole("ADMIN");
    }

    @Test
    public void exitStopsCompetitionWithoutRebuildingOrStartingTraining() {
        ModeTransitionView result = service.planExit(AGENT_ID, admin);

        assertEquals("TRAINING", result.getTargetMode());
        assertEquals("STOP_COMPETITION_ENVIRONMENT", result.getSteps().get(0).getActionType());
        assertEquals(COMPETITION_ID, result.getSteps().get(0).getEnvironmentId());
        assertEquals(1, result.getSteps().size());
        org.mockito.Mockito.verify(snapshots, org.mockito.Mockito.never()).selectByTransition(anyString());
    }

    private ModeTransitionRecord entryTransition() {
        ModeTransitionRecord result = new ModeTransitionRecord();
        result.setTransitionId("entry-transition");
        result.setAgentId(AGENT_ID);
        result.setSourceMode("TRAINING");
        result.setTargetMode("COMPETITION");
        result.setState("SUCCEEDED");
        return result;
    }

    private ModeTransitionStepRecord step(String transitionId, int phase, String kind,
                                          String environmentId, String action, String state) {
        ModeTransitionStepRecord result = new ModeTransitionStepRecord();
        result.setTransitionId(transitionId);
        result.setPhaseNumber(phase);
        result.setStepOrdinal(1);
        result.setEnvironmentKind(kind);
        result.setEnvironmentId(environmentId);
        result.setActionType(action);
        result.setState(state);
        return result;
    }
}
