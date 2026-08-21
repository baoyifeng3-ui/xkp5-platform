package com.match.mode.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.service.AgentAuditService;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.model.ModeTransitionView;
import com.match.mode.persistence.ModeTrainingSnapshotMapper;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionQueryTest {
    @Test
    public void recentListIsCappedAndMapsOnlySafeStepFields() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ModeTransitionRecord transition = new ModeTransitionRecord();
        transition.setTransitionId("transition-1");
        transition.setAgentId("agent-1");
        transition.setSourceMode("TRAINING");
        transition.setTargetMode("COMPETITION");
        transition.setState("RUNNING");
        transition.setRequestedAt(LocalDateTime.of(2026, 8, 21, 1, 2, 3));
        ModeTransitionStepRecord step = new ModeTransitionStepRecord();
        step.setStepId("step-1");
        step.setTransitionId("transition-1");
        step.setPhaseNumber(1);
        step.setStepOrdinal(1);
        step.setEnvironmentKind("COMPETITION");
        step.setEnvironmentId("environment-1");
        step.setActionType("START_COMPETITION_ENVIRONMENT");
        step.setState("DISPATCHED");
        step.setCommandId("secret-command-id");
        step.setComponentResultsJson("{\"secret\":true}");
        when(transitions.selectRecent(100)).thenReturn(Collections.singletonList(transition));
        when(steps.selectByTransition("transition-1")).thenReturn(Collections.singletonList(step));
        ModeTransitionService service = service(transitions, steps);

        List<ModeTransitionView> result = service.list(999);

        assertEquals(1, result.size());
        assertEquals("transition-1", result.get(0).getTransitionId());
        assertEquals("step-1", result.get(0).getSteps().get(0).getStepId());
        verify(transitions).selectRecent(100);
        assertFalse(hasField(result.get(0).getSteps().get(0).getClass(), "commandId"));
        assertFalse(hasField(result.get(0).getSteps().get(0).getClass(), "componentResultsJson"));
    }

    @Test
    public void detailReadUsesPrimaryKeyLookupWithoutWriteLock() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ModeTransitionRecord transition = new ModeTransitionRecord();
        transition.setTransitionId("transition-1");
        transition.setAgentId("agent-1");
        transition.setSourceMode("TRAINING");
        transition.setTargetMode("COMPETITION");
        transition.setState("RUNNING");
        transition.setRequestedAt(LocalDateTime.of(2026, 8, 21, 1, 2, 3));
        when(transitions.selectById("transition-1")).thenReturn(transition);
        when(steps.selectByTransition("transition-1")).thenReturn(Collections.emptyList());

        ModeTransitionView result = service(transitions, steps).get("transition-1");

        assertEquals("transition-1", result.getTransitionId());
        verify(transitions).selectById("transition-1");
        verify(transitions, never()).selectForUpdate("transition-1");
    }

    private ModeTransitionService service(ModeTransitionMapper transitions,
                                          ModeTransitionStepMapper steps) {
        return new ModeTransitionService(mock(ProcessingAgentModeMapper.class), transitions, steps,
                mock(ModeTrainingSnapshotMapper.class), mock(ProcessingAgentMapper.class),
                mock(TrainingEnvironmentMapper.class), mock(CompetitionEnvironmentMapper.class),
                mock(ProcessingEnvironmentSlotMapper.class), mock(EnvironmentOperationMapper.class),
                mock(AgentCommandService.class), mock(EnvironmentCommandFactory.class),
                mock(AgentAuditService.class), Clock.fixed(
                Instant.parse("2026-08-21T01:02:03Z"), ZoneOffset.UTC));
    }

    private boolean hasField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException expected) {
            return false;
        }
    }
}
