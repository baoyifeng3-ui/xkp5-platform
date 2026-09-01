package com.match.service.impl;

import com.match.dto.CountDownResponse;
import com.match.mode.model.PlatformModeView;
import com.match.mode.service.PlatformModeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantLoginGateTest {
    @Mock
    private CountDownServiceImpl countDownService;

    @Mock
    private PlatformModeService platformModeService;

    private ParticipantLoginGate gate;

    @Before
    public void setUp() {
        when(platformModeService.current()).thenReturn(new PlatformModeView(PlatformModeService.COMPETITION, 1, null, null));
        gate = new ParticipantLoginGate(countDownService, platformModeService, true);
    }

    @Test
    public void alwaysAllowsAdmin() {
        assertNull(gate.deniedMessage(true));
    }

    @Test
    public void allowsRunningAndPausedParticipants() {
        when(countDownService.snapshot()).thenReturn(snapshot("RUNNING"), snapshot("PAUSED"));

        assertNull(gate.deniedMessage(false));
        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantWhenNoScheduleExists() {
        when(countDownService.snapshot()).thenReturn(snapshot("NOT_STARTED"));

        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantBeforeLoginWindow() {
        when(countDownService.snapshot()).thenReturn(snapshot("WAITING_LOGIN"));
        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantDuringPreStartWindow() {
        when(countDownService.snapshot()).thenReturn(snapshot("SCHEDULED"));

        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantAfterFinishToReachTheCompetitionShell() {
        when(countDownService.snapshot()).thenReturn(snapshot("FINISHED"));
        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantsWhenGateIsDisabledForTesting() {
        ParticipantLoginGate testGate = new ParticipantLoginGate(countDownService, platformModeService, false);
        assertNull(testGate.deniedMessage(false));
    }

    @Test
    public void alwaysAllowsParticipantsInTrainingMode() {
        when(platformModeService.current()).thenReturn(new PlatformModeView(PlatformModeService.TRAINING, 1, null, null));
        when(countDownService.snapshot()).thenReturn(snapshot("FINISHED"));
        assertNull(gate.deniedMessage(false));
    }

    private CountDownResponse snapshot(String status) {
        CountDownResponse response = new CountDownResponse();
        response.setStatus(status);
        return response;
    }
}
