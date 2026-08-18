package com.match.service.impl;

import com.match.dto.CountDownResponse;
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

    private ParticipantLoginGate gate;

    @Before
    public void setUp() {
        gate = new ParticipantLoginGate(countDownService, true);
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
    public void rejectsParticipantBeforeLoginWindow() {
        when(countDownService.snapshot()).thenReturn(snapshot("WAITING_LOGIN"));

        assertEquals("尚未到开放登录时间，请稍后再试", gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantDuringPreStartWindow() {
        when(countDownService.snapshot()).thenReturn(snapshot("SCHEDULED"));

        assertNull(gate.deniedMessage(false));
    }

    @Test
    public void rejectsParticipantAfterFinish() {
        when(countDownService.snapshot()).thenReturn(snapshot("FINISHED"));

        assertEquals("比赛已结束，无法登录", gate.deniedMessage(false));
    }

    @Test
    public void allowsParticipantsWhenGateIsDisabledForTesting() {
        ParticipantLoginGate testGate = new ParticipantLoginGate(countDownService, false);
        assertNull(testGate.deniedMessage(false));
    }

    private CountDownResponse snapshot(String status) {
        CountDownResponse response = new CountDownResponse();
        response.setStatus(status);
        return response;
    }
}
