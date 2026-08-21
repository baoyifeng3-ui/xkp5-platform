package com.match.mode.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionRecoveryTest {
    @Test
    public void recoveryScansAtMostOneHundredRowsAndIsolatesFailures() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        ModeTransitionReconciler reconciler = mock(ModeTransitionReconciler.class);
        ModeTransitionService service = mock(ModeTransitionService.class);
        List<ModeTransitionRecord> active = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ModeTransitionRecord transition = new ModeTransitionRecord();
            transition.setTransitionId("transition-" + i);
            active.add(transition);
        }
        when(transitions.selectActiveForRecovery(100)).thenReturn(active.subList(0, 100));

        ModeTransitionRecovery recovery = new ModeTransitionRecovery(transitions, steps, commands,
                reconciler, service);
        recovery.recover();

        verify(transitions).selectActiveForRecovery(100);
        verify(service, org.mockito.Mockito.times(100))
                .advanceAfterSuccessfulStep(org.mockito.ArgumentMatchers.anyString());
        verify(service, never()).dispatchReadyPhase(anyString());
    }

    @Test
    public void recoveryReconcilesTerminalCommandThenUsesNormalAdvancementPath() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionStepMapper steps = mock(ModeTransitionStepMapper.class);
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        ModeTransitionReconciler reconciler = mock(ModeTransitionReconciler.class);
        ModeTransitionService service = mock(ModeTransitionService.class);
        ModeTransitionRecord transition = new ModeTransitionRecord();
        transition.setTransitionId("transition-1");
        ModeTransitionStepRecord step = new ModeTransitionStepRecord();
        step.setState("DISPATCHED");
        step.setCommandId("command-1");
        ProcessingAgentCommandRecord command = new ProcessingAgentCommandRecord();
        command.setCommandId("command-1");
        command.setState("SUCCEEDED");
        command.setResultCode("ENVIRONMENT_STOPPED");
        command.setResultMessage("stopped");
        command.setResultJson("{\"pair\":{}}");
        when(transitions.selectActiveForRecovery(100))
                .thenReturn(Collections.singletonList(transition));
        when(steps.selectByTransition("transition-1"))
                .thenReturn(Collections.singletonList(step));
        when(commands.selectById("command-1")).thenReturn(command);

        ModeTransitionRecovery recovery = new ModeTransitionRecovery(transitions, steps, commands,
                reconciler, service);
        recovery.recover();

        verify(reconciler).reconcileIfPresent("command-1", true, "ENVIRONMENT_STOPPED",
                "stopped", "{\"pair\":{}}");
        verify(service).advanceAfterSuccessfulStep("transition-1");
    }
}
