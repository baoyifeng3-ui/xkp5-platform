package com.match.mode.service;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionReconcilerTest {
    private ModeTransitionStepMapper steps;
    private ModeTransitionMapper transitions;
    private ProcessingAgentModeMapper modes;
    private ModeTransitionReconciler reconciler;
    private ModeTransitionStepRecord step;
    private ModeTransitionRecord transition;
    private ProcessingAgentModeRecord mode;

    @Before
    public void setUp() {
        steps = mock(ModeTransitionStepMapper.class);
        transitions = mock(ModeTransitionMapper.class);
        modes = mock(ProcessingAgentModeMapper.class);
        ModeTransitionService service = mock(ModeTransitionService.class);
        reconciler = new ModeTransitionReconciler(steps, transitions, modes, service,
                Clock.fixed(Instant.parse("2026-08-21T06:07:08Z"), ZoneOffset.UTC));
        step = new ModeTransitionStepRecord();
        step.setStepId("step-1");
        step.setTransitionId("transition-1");
        step.setPhaseNumber(1);
        step.setState("DISPATCHED");
        transition = new ModeTransitionRecord();
        transition.setTransitionId("transition-1");
        transition.setAgentId("agent-1");
        transition.setState("RUNNING");
        transition.setTargetMode("TRAINING");
        mode = new ProcessingAgentModeRecord();
        mode.setAgentId("agent-1");
        mode.setActiveTransitionId("transition-1");
        when(steps.selectByCommandForUpdate("command-1")).thenReturn(step);
        when(transitions.selectForUpdate("transition-1")).thenReturn(transition);
        when(modes.selectForUpdate("agent-1")).thenReturn(mode);
    }

    @Test
    public void legacyRestoreReportsStoppedInsteadOfRunning() {
        com.match.environment.persistence.TrainingEnvironmentMapper training = mock(com.match.environment.persistence.TrainingEnvironmentMapper.class);
        com.match.environment.persistence.CompetitionEnvironmentMapper competition = mock(com.match.environment.persistence.CompetitionEnvironmentMapper.class);
        reconciler = new ModeTransitionReconciler(steps, transitions, modes, training, competition,
                mock(ModeTransitionService.class), new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-21T06:07:08Z"), ZoneOffset.UTC));
        step.setEnvironmentKind("TRAINING");
        step.setEnvironmentId("env");
        step.setActionType("RESTORE_TRAINING_ENVIRONMENT");
        reconciler.reconcileIfPresent("command-1", true, "ENVIRONMENT_RESTORED", "ok", null);
        verify(training).reconcileModeStep(org.mockito.ArgumentMatchers.eq("env"), org.mockito.ArgumentMatchers.eq("STOPPED"),
                org.mockito.ArgumentMatchers.eq("UNKNOWN"), org.mockito.ArgumentMatchers.eq("UNKNOWN"), any());
    }

    @Test
    public void failedStepBecomesDegradedWithoutChangingSuccessfulSteps() {
        assertTrue(reconciler.reconcileIfPresent("command-1", false,
                "DOCKER_TIMEOUT", "timeout", null));

        verify(steps).markTerminal("step-1", "FAILED", "DOCKER_TIMEOUT", "timeout", null,
                java.time.LocalDateTime.of(2026, 8, 21, 6, 7, 8));
        verify(transitions).updateState("transition-1", "DEGRADED", "timeout",
                java.time.LocalDateTime.of(2026, 8, 21, 6, 7, 8));
        verify(modes).updateTransition("agent-1", "TRAINING", "DEGRADED", "transition-1",
                java.time.LocalDateTime.of(2026, 8, 21, 6, 7, 8));
        assertEquals("FAILED", step.getState());
    }
}
