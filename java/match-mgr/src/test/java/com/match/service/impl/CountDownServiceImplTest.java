package com.match.service.impl;

import com.match.dto.CountDownActionRequest;
import com.match.dto.CountDownResponse;
import com.match.entity.CountDown;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class CountDownServiceImplTest {
    @Mock private CompetitionRoundResetService roundResetService;
    private CountDownServiceImpl service;
    private CountDown countDown;

    @Before
    public void setUp() {
        service = spy(new CountDownServiceImpl(roundResetService));
        countDown = new CountDown();
        countDown.setCountDownId(1);
        countDown.setStatus("NOT_STARTED");
        countDown.setRemainingSeconds(0L);
        doReturn(countDown).when(service).getById(1);
        doReturn(true).when(service).updateById(any(CountDown.class));
    }

    @Test
    public void schedulesWaitingLoginWindow() {
        long start = System.currentTimeMillis() + 20 * 60_000L;
        CountDownResponse response = service.apply(schedule(start, 5, 180), 7);

        assertEquals("WAITING_LOGIN", response.getStatus());
        assertEquals("BEFORE_LOGIN", response.getAccessPhase());
        assertEquals(Integer.valueOf(5), response.getPreLoginMinutes());
        assertEquals(Integer.valueOf(180), response.getDurationMinutes());
        assertNotNull(response.getLoginOpenTime());
        assertEquals(start - 5 * 60_000L, response.getLoginOpenTime().longValue());
    }

    @Test
    public void schedulesParticipantPreStartWindow() {
        long start = System.currentTimeMillis() + 60_000L;
        CountDownResponse response = service.apply(schedule(start, 5, 240), 7);

        assertEquals("SCHEDULED", response.getStatus());
        assertEquals("PRE_START", response.getAccessPhase());
    }

    @Test
    public void resetsRoundWhenRestartingFinishedCompetition() {
        countDown.setStatus("FINISHED");

        service.apply(action("START", 240), 7);

        verify(roundResetService).reset();
        assertEquals("RUNNING", countDown.getStatus());
    }

    @Test
    public void doesNotResetRoundOnFirstStart() {
        service.apply(action("START", 240), 7);

        verify(roundResetService, never()).reset();
        assertEquals("RUNNING", countDown.getStatus());
    }

    @Test
    public void rejectsEndingAlreadyFinishedCompetition() {
        countDown.setStatus("FINISHED");

        try {
            service.apply(action("END", null), 7);
            fail("已结束比赛不应重复结束");
        } catch (IllegalArgumentException exception) {
            assertEquals("比赛已经结束，不能重复结束", exception.getMessage());
        }
    }

    private CountDownActionRequest schedule(long start, int preLoginMinutes, int durationMinutes) {
        CountDownActionRequest request = new CountDownActionRequest();
        request.setAction("SCHEDULE");
        request.setStartTime(start);
        request.setPreLoginMinutes(preLoginMinutes);
        request.setDurationMinutes(durationMinutes);
        return request;
    }

    private CountDownActionRequest action(String action, Integer minutes) {
        CountDownActionRequest request = new CountDownActionRequest();
        request.setAction(action);
        request.setMinutes(minutes);
        return request;
    }
}
