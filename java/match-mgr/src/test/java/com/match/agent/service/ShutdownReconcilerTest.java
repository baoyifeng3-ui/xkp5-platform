package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.RunningShutdownCandidate;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ShutdownReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:02:00Z");
    private ProcessingAgentCommandMapper mapper;
    private ShutdownReconciler reconciler;

    @Before
    public void setUp() {
        mapper = mock(ProcessingAgentCommandMapper.class);
        reconciler = new ShutdownReconciler(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void confirmsOfflineAgentAfterShutdownStartWindow() {
        RunningShutdownCandidate candidate = candidate("offline", 30, 20);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.singletonList(candidate));

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).confirmShutdownOffline("offline", now);
        verify(mapper, never()).failShutdownConfirmation("offline", now);
    }

    @Test
    public void failsCommandWhenAgentRemainsOnlinePastDeadline() {
        RunningShutdownCandidate candidate = candidate("online-timeout", 91, 1);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.singletonList(candidate));

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).failShutdownConfirmation("online-timeout", now);
        verify(mapper, never()).confirmShutdownOffline("online-timeout", now);
    }

    @Test
    public void leavesYoungOrRecentlySeenCommandsRunning() {
        RunningShutdownCandidate young = candidate("young", 10, 20);
        RunningShutdownCandidate recentlySeen = candidate("recent", 30, 1);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Arrays.asList(young, recentlySeen));

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper, never()).confirmShutdownOffline("young", now);
        verify(mapper, never()).failShutdownConfirmation("young", now);
        verify(mapper, never()).confirmShutdownOffline("recent", now);
        verify(mapper, never()).failShutdownConfirmation("recent", now);
    }

    private RunningShutdownCandidate candidate(String id, long startedSecondsAgo, long seenSecondsAgo) {
        RunningShutdownCandidate candidate = new RunningShutdownCandidate();
        candidate.setCommandId(id);
        candidate.setStartedAt(LocalDateTime.ofInstant(NOW.minusSeconds(startedSecondsAgo), ZoneOffset.UTC));
        candidate.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(seenSecondsAgo), ZoneOffset.UTC));
        return candidate;
    }
}
