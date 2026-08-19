package com.match.terminal.service;

import com.match.agent.service.AgentAuditService;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import com.match.terminal.relay.TerminalPeer;
import com.match.terminal.relay.TerminalRelayCoordinator;
import org.junit.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalSessionExpiryTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    public void expiryUpdateAndAuditShareOnePerRowTransaction() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord row = row("s1", "ACTIVE");
        row.setAbsoluteExpiresAt(utc(NOW));
        when(mapper.selectExpired(utc(NOW.minusSeconds(600)), utc(NOW), 100))
                .thenReturn(Collections.singletonList(row));
        when(mapper.closeExpired(eq("s1"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any())).thenReturn(1);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenAnswer(TerminalSessionExpiryTest::runConditional);
        int[] transactionCalls = {0};
        boolean[] inTransaction = {false};
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactionCalls[0]++;
                inTransaction[0] = true;
                try {
                    return action.doInTransaction(mock(TransactionStatus.class));
                } finally {
                    inTransaction[0] = false;
                }
            }
        };
        org.mockito.Mockito.doAnswer(invocation -> {
            assertTrue(inTransaction[0]);
            return null;
        }).when(audit).recordTerminal(eq("TERMINAL_TIMEOUT"), eq("SUCCESS"),
                eq("ABSOLUTE_EXPIRED"), any(), any(String.class), eq("s1"), any(String.class));

        new TerminalSessionExpiry(mapper, lifecycle, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), null, transactions).expire();

        assertEquals(1, transactionCalls[0]);
    }

    @Test
    public void expiryFailureIsIsolatedPerRowAndFailsLocalPeersClosed() throws Exception {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionService relaySessions = mock(TerminalSessionService.class);
        when(relaySessions.isRelayAttachmentEligible(any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(true);
        when(relaySessions.markRelayActive("s1")).thenReturn(true);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                relaySessions, Runnable::run, () -> 0L);
        WebSocketSession browserSocket = mock(WebSocketSession.class);
        WebSocketSession agentSocket = mock(WebSocketSession.class);
        when(browserSocket.isOpen()).thenReturn(true);
        when(agentSocket.isOpen()).thenReturn(true);
        assertTrue(coordinator.attach("s1",
                new TerminalPeer(TerminalPeer.Role.BROWSER, null, browserSocket)));
        assertTrue(coordinator.attach("s1",
                new TerminalPeer(TerminalPeer.Role.AGENT, "agent", agentSocket)));

        TerminalSessionRecord first = row("s1", "ACTIVE");
        first.setAbsoluteExpiresAt(utc(NOW));
        TerminalSessionRecord second = row("s2", "ACTIVE");
        second.setAbsoluteExpiresAt(utc(NOW));
        when(mapper.selectExpired(utc(NOW.minusSeconds(600)), utc(NOW), 100))
                .thenReturn(Arrays.asList(first, second), Collections.singletonList(first));
        when(mapper.closeExpired(eq("s1"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any())).thenReturn(1, 1);
        when(mapper.closeExpired(eq("s2"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable")).doNothing()
                .when(audit).recordTerminal(eq("TERMINAL_TIMEOUT"), eq("SUCCESS"),
                        eq("ABSOLUTE_EXPIRED"), any(), any(String.class), eq("s1"),
                        any(String.class));
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
        TerminalSessionExpiry expiry = new TerminalSessionExpiry(mapper, coordinator, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), null, transactions);

        expiry.expire();
        expiry.expire();

        verify(browserSocket).close(any(CloseStatus.class));
        verify(agentSocket).close(any(CloseStatus.class));
        verify(mapper, times(2)).closeExpired(eq("s1"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any());
        verify(mapper).closeExpired(eq("s2"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any());
        verify(audit, times(2)).recordTerminal(eq("TERMINAL_TIMEOUT"), eq("SUCCESS"),
                eq("ABSOLUTE_EXPIRED"), any(), any(String.class), eq("s1"), any(String.class));
        verify(audit).recordTerminal(eq("TERMINAL_TIMEOUT"), eq("SUCCESS"),
                eq("ABSOLUTE_EXPIRED"), any(), any(String.class), eq("s2"), any(String.class));
    }

    @Test
    public void expiresAtInclusiveBoundariesWithStablePrecedenceAndBatchLimit() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord absolute = row("s1", "ACTIVE");
        absolute.setAbsoluteExpiresAt(utc(NOW));
        absolute.setLastIoAt(utc(NOW.minusSeconds(600)));
        TerminalSessionRecord agent = row("s2", "WAITING_AGENT");
        agent.setRequestedAt(utc(NOW.minusSeconds(90)));
        agent.setAbsoluteExpiresAt(utc(NOW.plusSeconds(1)));
        TerminalSessionRecord browser = row("s3", "WAITING_BROWSER");
        browser.setAgentConnectedAt(utc(NOW.minusSeconds(60)));
        browser.setAbsoluteExpiresAt(utc(NOW.plusSeconds(1)));
        TerminalSessionRecord idle = row("s4", "ACTIVE");
        idle.setLastIoAt(utc(NOW.minusSeconds(600)));
        idle.setAbsoluteExpiresAt(utc(NOW.plusSeconds(1)));
        when(mapper.selectExpired(utc(NOW.minusSeconds(600)), utc(NOW), 100))
                .thenReturn(Arrays.asList(absolute, agent, browser, idle));
        when(lifecycle.closePersistedSessionConditionally(any(String.class), any()))
                .thenAnswer(TerminalSessionExpiryTest::runConditional);
        when(mapper.closeExpired(eq("s1"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any())).thenReturn(1);
        when(mapper.closeExpired(eq("s2"), any(), any(), eq("FAILED"),
                eq("AGENT_CONNECT_TIMEOUT"), any())).thenReturn(1);
        when(mapper.closeExpired(eq("s3"), any(), any(), eq("FAILED"),
                eq("BROWSER_CONNECT_TIMEOUT"), any())).thenReturn(1);
        when(mapper.closeExpired(eq("s4"), any(), any(), eq("CLOSED"),
                eq("IDLE_TIMEOUT"), any())).thenReturn(1);

        new TerminalSessionExpiry(mapper, lifecycle, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), null).expire();

        verify(mapper).selectExpired(utc(NOW.minusSeconds(600)), utc(NOW), 100);
        verify(mapper).closeExpired(eq("s1"), any(), any(), eq("CLOSED"),
                eq("ABSOLUTE_EXPIRED"), any());
        verify(mapper).closeExpired(eq("s2"), any(), any(), eq("FAILED"),
                eq("AGENT_CONNECT_TIMEOUT"), any());
        verify(mapper).closeExpired(eq("s3"), any(), any(), eq("FAILED"),
                eq("BROWSER_CONNECT_TIMEOUT"), any());
        verify(mapper).closeExpired(eq("s4"), any(), any(), eq("CLOSED"),
                eq("IDLE_TIMEOUT"), any());
    }

    @Test
    public void startupRecoveryKeepsAttachmentGateClosedUntilAllGuardedRowsFinish() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalSessionRecord row = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        when(mapper.recover("s1", utc(NOW), utc(NOW))).thenReturn(1);
        TerminalSessionExpiry expiry = new TerminalSessionExpiry(mapper, lifecycle, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), sessions);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any())).thenAnswer(invocation -> {
            verify(sessions, never()).completeStartupRecovery();
            return runConditional(invocation);
        });

        expiry.recoverOnStartup();

        verify(mapper, org.mockito.Mockito.times(2)).selectRecoverable(utc(NOW), 100);
        verify(mapper).recover("s1", utc(NOW), utc(NOW));
        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void startupRecoveryFailureLeavesGateClosed() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        when(mapper.selectRecoverable(utc(NOW), 100)).thenThrow(new IllegalStateException("db"));
        TerminalSessionExpiry expiry = new TerminalSessionExpiry(mapper,
                mock(TerminalRelayLifecycle.class), mock(AgentAuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), sessions);
        boolean failed = false;
        try {
            expiry.recoverOnStartup();
        } catch (IllegalStateException expected) {
            failed = true;
        }
        assertTrue(failed);
        verify(sessions, never()).completeStartupRecovery();
    }

    @Test
    public void recoveryCompletesWhenGuardedUpdateLostABenignRace() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalSessionRecord row = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenReturn(TerminalRelayLifecycle.ConditionalCloseResult.LOCAL_ONLY);

        new TerminalSessionExpiry(mapper, lifecycle, mock(AgentAuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();

        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void recoveryContinuesAfterRemoteWinnerClearsEntireSelectedBatch() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalSessionRecord remoteWinner = row("s1", "ACTIVE");
        TerminalSessionRecord nextBatch = row("s2", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(
                Collections.singletonList(remoteWinner), Collections.singletonList(nextBatch),
                Collections.singletonList(nextBatch), Collections.emptyList());
        when(mapper.recover("s2", utc(NOW), utc(NOW))).thenReturn(1);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenReturn(TerminalRelayLifecycle.ConditionalCloseResult.LOCAL_ONLY);
        when(lifecycle.closePersistedSessionConditionally(eq("s2"), any()))
                .thenAnswer(TerminalSessionExpiryTest::runConditional);

        new TerminalSessionExpiry(mapper, lifecycle, mock(AgentAuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();

        verify(mapper).recover("s2", utc(NOW), utc(NOW));
        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void recoveryFailsClosedWhenSelectedRowsRemainEligibleWithoutProgress() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalSessionRecord stuck = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(
                Collections.singletonList(stuck), Collections.singletonList(stuck));
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenReturn(TerminalRelayLifecycle.ConditionalCloseResult.KEPT_OPEN);

        boolean failed = false;
        try {
            new TerminalSessionExpiry(mapper, lifecycle, mock(AgentAuditService.class),
                    Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();
        } catch (IllegalStateException expected) {
            failed = true;
        }

        assertTrue(failed);
        verify(sessions, never()).completeStartupRecovery();
    }

    @Test
    public void recoveryDoesNotTrustLocalOnlyWhenDatabaseRowIsStillEligible() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalSessionRecord stuck = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(
                Collections.singletonList(stuck), Collections.singletonList(stuck));
        when(mapper.selectById("s1")).thenReturn(stuck);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenReturn(TerminalRelayLifecycle.ConditionalCloseResult.LOCAL_ONLY);

        boolean failed = false;
        try {
            new TerminalSessionExpiry(mapper, lifecycle, mock(AgentAuditService.class),
                    Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();
        } catch (IllegalStateException expected) {
            failed = true;
        }

        assertTrue(failed);
        verify(sessions, never()).completeStartupRecovery();
    }

    @Test
    public void recoveryAuditFailureDoesNotStrandStartupGate() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord row = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(utc(NOW), 100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        when(mapper.recover("s1", utc(NOW), utc(NOW))).thenReturn(1);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionConditionally(eq("s1"), any()))
                .thenAnswer(TerminalSessionExpiryTest::runConditional);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable")).when(audit)
                .recordTerminal(eq("TERMINAL_RECOVERY"), any(String.class), any(String.class),
                        any(), any(String.class), any(String.class), any(String.class));

        new TerminalSessionExpiry(mapper, lifecycle, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();

        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void schedulerDoesNotExpireRowsBeforeStartupRecoveryCompletes() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        when(sessions.isStartupRecoveryComplete()).thenReturn(false);

        new TerminalSessionExpiry(mapper, mock(TerminalRelayLifecycle.class),
                mock(AgentAuditService.class), Clock.fixed(NOW, ZoneOffset.UTC), sessions).expire();

        verify(mapper, never()).selectExpired(any(LocalDateTime.class), any(LocalDateTime.class),
                eq(100));
    }

    private static TerminalSessionRecord row(String id, String state) {
        TerminalSessionRecord row = new TerminalSessionRecord();
        row.setSessionId(id);
        row.setAgentId("11111111-1111-4111-8111-111111111111");
        row.setCommandId("22222222-2222-4222-8222-222222222222");
        row.setState(state);
        row.setRequestedAt(utc(NOW.minusSeconds(30)));
        return row;
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    @SuppressWarnings("unchecked")
    private static TerminalRelayLifecycle.ConditionalCloseResult runConditional(
            org.mockito.invocation.InvocationOnMock invocation) {
        java.util.function.Supplier<TerminalRelayLifecycle.ConditionalCloseDecision> action =
                invocation.getArgument(1);
        TerminalRelayLifecycle.ConditionalCloseDecision decision = action.get();
        return decision == TerminalRelayLifecycle.ConditionalCloseDecision.PERSISTED
                ? TerminalRelayLifecycle.ConditionalCloseResult.PERSISTED
                : decision == TerminalRelayLifecycle.ConditionalCloseDecision.LOCAL_ONLY
                ? TerminalRelayLifecycle.ConditionalCloseResult.LOCAL_ONLY
                : TerminalRelayLifecycle.ConditionalCloseResult.KEPT_OPEN;
    }
}
