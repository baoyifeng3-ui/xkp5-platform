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
    private AgentAuditService audit;

    @Before
    public void setUp() {
        mapper = mock(ProcessingAgentCommandMapper.class);
        audit = mock(AgentAuditService.class);
        reconciler = new ShutdownReconciler(mapper, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.selectExpiredLeases(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), 100))
                .thenReturn(Collections.emptyList());
    }

    @Test
    public void confirmsOfflineAgentAfterShutdownStartWindow() {
        RunningShutdownCandidate candidate = candidate("offline", 30, 20);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.singletonList(candidate));
        when(mapper.confirmShutdownOffline("offline", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .thenReturn(1);

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).confirmShutdownOffline("offline", now);
        verify(audit).recordCommandSuccess("SHUTDOWN_OFFLINE_CONFIRMED", null, "agent-id", "offline");
        verify(mapper, never()).failShutdownConfirmation("offline", now);
    }

    @Test
    public void failsCommandWhenAgentRemainsOnlinePastDeadline() {
        RunningShutdownCandidate candidate = candidate("online-timeout", 91, 1);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.singletonList(candidate));
        when(mapper.failShutdownConfirmation("online-timeout",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(1);

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).failShutdownConfirmation("online-timeout", now);
        verify(audit).recordCommandFailure("SHUTDOWN_CONFIRMATION", "SHUTDOWN_NOT_CONFIRMED",
                null, "agent-id", "online-timeout");
        verify(mapper, never()).confirmShutdownOffline("online-timeout", now);
    }

    @Test
    public void doesNotConfirmOfflineAfterTheDeadlineWindowHasAlreadyBeenMissed() {
        RunningShutdownCandidate candidate = candidate("late-offline", 100, 20);
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.singletonList(candidate));

        reconciler.reconcile();

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).failShutdownConfirmation("late-offline", now);
        verify(mapper, never()).confirmShutdownOffline("late-offline", now);
    }

    @Test
    public void globallyRequeuesOrFailsExpiredLeasesWithoutAgentPolling() {
        com.match.agent.persistence.ProcessingAgentCommandRecord retry = expiredLease("retry", 2);
        com.match.agent.persistence.ProcessingAgentCommandRecord exhausted = expiredLease("exhausted", 5);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(mapper.selectExpiredLeases(now, 100)).thenReturn(Arrays.asList(retry, exhausted));
        when(mapper.selectRunningShutdowns(100)).thenReturn(Collections.emptyList());
        when(mapper.requeueExpiredLease("retry", now, 5)).thenReturn(1);
        when(mapper.failExpiredLease("exhausted", now, 5)).thenReturn(1);

        reconciler.reconcile();

        verify(mapper).requeueExpiredLease("retry", now, 5);
        verify(mapper).failExpiredLease("exhausted", now, 5);
        verify(audit).recordCommandFailure("COMMAND_DELIVERY", "LEASE_EXPIRED",
                null, "agent-id", "retry");
        verify(audit).recordCommandFailure("COMMAND_DELIVERY", "DELIVERY_ATTEMPTS_EXHAUSTED",
                null, "agent-id", "exhausted");
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
        candidate.setAgentId("agent-id");
        candidate.setStartedAt(LocalDateTime.ofInstant(NOW.minusSeconds(startedSecondsAgo), ZoneOffset.UTC));
        candidate.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(seenSecondsAgo), ZoneOffset.UTC));
        return candidate;
    }

    private com.match.agent.persistence.ProcessingAgentCommandRecord expiredLease(String id, int attempts) {
        com.match.agent.persistence.ProcessingAgentCommandRecord record =
                new com.match.agent.persistence.ProcessingAgentCommandRecord();
        record.setCommandId(id);
        record.setAgentId("agent-id");
        record.setAttemptCount(attempts);
        return record;
    }
}
