package com.match.mode.service;

import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModeTransitionRecoveryTest {
    @Test
    public void recoveryScansAtMostOneHundredRowsAndIsolatesFailures() {
        ModeTransitionMapper transitions = mock(ModeTransitionMapper.class);
        ModeTransitionService service = mock(ModeTransitionService.class);
        List<ModeTransitionRecord> active = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ModeTransitionRecord transition = new ModeTransitionRecord();
            transition.setTransitionId("transition-" + i);
            active.add(transition);
        }
        when(transitions.selectActiveForRecovery(100)).thenReturn(active.subList(0, 100));

        ModeTransitionRecovery recovery = new ModeTransitionRecovery(transitions, service);
        recovery.recover();

        verify(transitions).selectActiveForRecovery(100);
        verify(service, org.mockito.Mockito.times(100)).dispatchReadyPhase(org.mockito.ArgumentMatchers.anyString());
    }
}
