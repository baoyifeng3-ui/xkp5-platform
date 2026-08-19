package com.match.terminal.service;

import com.match.agent.service.AgentAuditService;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalSessionExpiryTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

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
        when(lifecycle.closePersistedSessionIf(any(String.class), any()))
                .thenAnswer(invocation -> {
                    java.util.function.BooleanSupplier action = invocation.getArgument(1);
                    return action.getAsBoolean();
                });
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
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        when(mapper.recover("s1", utc(NOW))).thenReturn(1);
        TerminalSessionExpiry expiry = new TerminalSessionExpiry(mapper, lifecycle, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), sessions);
        when(lifecycle.closePersistedSessionIf(eq("s1"), any())).thenAnswer(invocation -> {
            verify(sessions, never()).completeStartupRecovery();
            return ((java.util.function.BooleanSupplier) invocation.getArgument(1)).getAsBoolean();
        });

        expiry.recoverOnStartup();

        verify(mapper, org.mockito.Mockito.times(2)).selectRecoverable(100);
        verify(mapper).recover("s1", utc(NOW));
        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void startupRecoveryFailureLeavesGateClosed() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        when(mapper.selectRecoverable(100)).thenThrow(new IllegalStateException("db"));
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
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionIf(eq("s1"), any())).thenReturn(false);

        new TerminalSessionExpiry(mapper, lifecycle, mock(AgentAuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), sessions).recoverOnStartup();

        verify(sessions).completeStartupRecovery();
    }

    @Test
    public void recoveryAuditFailureDoesNotStrandStartupGate() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord row = row("s1", "ACTIVE");
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(row),
                Collections.emptyList());
        when(mapper.recover("s1", utc(NOW))).thenReturn(1);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        when(lifecycle.closePersistedSessionIf(eq("s1"), any())).thenAnswer(invocation ->
                ((java.util.function.BooleanSupplier) invocation.getArgument(1)).getAsBoolean());
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
}
