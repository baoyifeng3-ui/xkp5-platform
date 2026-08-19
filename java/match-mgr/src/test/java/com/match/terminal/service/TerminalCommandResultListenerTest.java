package com.match.terminal.service;

import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.service.AgentAuditService;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TerminalCommandResultListenerTest {
    @Test
    public void afterCommitWrapperIsNonTransactional() throws Exception {
        Transactional transactional = TerminalCommandResultListener.class
                .getMethod("onFinished", AgentCommandFinishedEvent.class)
                .getAnnotation(Transactional.class);
        assertNull(transactional);
    }

    @Test
    public void afterCommitWorkerFailureIsHiddenAndDurableScanRetriesInNewTransaction() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord row = row();
        ProcessingAgentCommandRecord command = failedCommand(row);
        when(mapper.selectCommandReconciliationCandidates(100))
                .thenReturn(Collections.singletonList(row));
        when(commands.selectById(row.getCommandId())).thenReturn(command);
        when(mapper.selectByCommandId(row.getCommandId())).thenReturn(row);
        when(mapper.closeCommandSession(eq(row.getSessionId()), eq(row.getCommandId()),
                eq(row.getAgentId()), eq("FAILED"), eq("PTY_START_FAILED"),
                eq("Terminal PTY command failed"), any())).thenReturn(1);
        when(lifecycle.closePersistedSessionConditionally(eq(row.getSessionId()), any()))
                .thenAnswer(TerminalCommandResultListenerTest::runConditional);
        AtomicInteger transactions = new AtomicInteger();
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                if (transactions.incrementAndGet() == 1) {
                    throw new IllegalStateException("worker transaction rolled back");
                }
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
        TerminalCommandResultListener listener = new TerminalCommandResultListener(mapper, commands,
                lifecycle, audit, Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC),
                transactionOperations);

        listener.onFinished(new AgentCommandFinishedEvent(row.getCommandId(), row.getAgentId(),
                "OPEN_ROOT_TERMINAL", false, "PTY_START_FAILED", "private result text"));
        listener.reconcileMissed();

        assertEquals(2, transactions.get());
        verify(mapper).closeCommandSession(eq(row.getSessionId()), eq(row.getCommandId()),
                eq(row.getAgentId()), eq("FAILED"), eq("PTY_START_FAILED"),
                eq("Terminal PTY command failed"), any());
        verify(audit).recordTerminal("TERMINAL_PTY_FAILURE", "FAILURE", "PTY_START_FAILED",
                null, row.getAgentId(), row.getSessionId(), row.getCommandId());
    }

    @Test
    public void failureClosesOnlyExactLiveSessionAndDoesNotStoreResultText() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord row = row();
        when(mapper.selectByCommandId(row.getCommandId())).thenReturn(row);
        when(mapper.closeCommandSession(row.getSessionId(), row.getCommandId(), row.getAgentId(),
                "FAILED", "PTY_START_FAILED", "Terminal PTY command failed", now())).thenReturn(1);
        when(lifecycle.closePersistedSessionConditionally(eq(row.getSessionId()), any()))
                .thenAnswer(TerminalCommandResultListenerTest::runConditional);

        new TerminalCommandResultListener(mapper, lifecycle, audit,
                Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC))
                .onFinished(new AgentCommandFinishedEvent(row.getCommandId(), row.getAgentId(),
                        "OPEN_ROOT_TERMINAL", false, "PTY_START_FAILED", "secret output"));

        verify(mapper).closeCommandSession(row.getSessionId(), row.getCommandId(), row.getAgentId(),
                "FAILED", "PTY_START_FAILED", "Terminal PTY command failed", now());
        verify(audit).recordTerminal("TERMINAL_PTY_FAILURE", "FAILURE", "PTY_START_FAILED",
                null, row.getAgentId(), row.getSessionId(), row.getCommandId());
    }

    @Test
    public void ignoresOtherCommandsAndStaleTerminalRows() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        TerminalCommandResultListener listener = new TerminalCommandResultListener(mapper, lifecycle,
                mock(AgentAuditService.class), Clock.systemUTC());
        listener.onFinished(new AgentCommandFinishedEvent("c", "a", "SHUTDOWN_SERVER",
                true, "OK", "ignored"));
        verify(mapper, never()).selectByCommandId(any(String.class));
    }

    @Test
    public void durableScanReconcilesCommittedTerminalCommandAfterLostEvent() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        TerminalSessionRecord row = row();
        ProcessingAgentCommandRecord command = new ProcessingAgentCommandRecord();
        command.setCommandId(row.getCommandId());
        command.setAgentId(row.getAgentId());
        command.setCommandType("OPEN_ROOT_TERMINAL");
        command.setState("FAILED");
        command.setResultCode("PTY_START_FAILED");
        command.setResultMessage("private result text");
        when(mapper.selectCommandReconciliationCandidates(100))
                .thenReturn(Collections.singletonList(row));
        when(commands.selectById(row.getCommandId())).thenReturn(command);
        when(mapper.selectByCommandId(row.getCommandId())).thenReturn(row);
        when(mapper.closeCommandSession(eq(row.getSessionId()), eq(row.getCommandId()),
                eq(row.getAgentId()), eq("FAILED"), eq("PTY_START_FAILED"),
                eq("Terminal PTY command failed"), any())).thenReturn(1);
        when(lifecycle.closePersistedSessionConditionally(eq(row.getSessionId()), any()))
                .thenAnswer(TerminalCommandResultListenerTest::runConditional);

        new TerminalCommandResultListener(mapper, commands, lifecycle,
                mock(AgentAuditService.class),
                Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC))
                .reconcileMissed();

        verify(mapper).closeCommandSession(eq(row.getSessionId()), eq(row.getCommandId()),
                eq(row.getAgentId()), eq("FAILED"), eq("PTY_START_FAILED"),
                eq("Terminal PTY command failed"), any());
    }

    @Test
    public void durableScanIsolatesCandidatesAndRetriesOnlyTheFailedOne() {
        TerminalSessionMapper mapper = mock(TerminalSessionMapper.class);
        ProcessingAgentCommandMapper commands = mock(ProcessingAgentCommandMapper.class);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        AgentAuditService audit = mock(AgentAuditService.class);
        TerminalSessionRecord first = row("33333333-3333-4333-8333-333333333331",
                "22222222-2222-4222-8222-222222222221");
        TerminalSessionRecord second = row("33333333-3333-4333-8333-333333333332",
                "22222222-2222-4222-8222-222222222222");
        TerminalSessionRecord third = row("33333333-3333-4333-8333-333333333333",
                "22222222-2222-4222-8222-222222222223");
        when(mapper.selectCommandReconciliationCandidates(100))
                .thenReturn(Arrays.asList(first, second, third), Collections.singletonList(second));
        when(mapper.selectByCommandId(first.getCommandId())).thenReturn(first);
        when(mapper.selectByCommandId(second.getCommandId())).thenReturn(second);
        when(mapper.selectByCommandId(third.getCommandId())).thenReturn(third);
        when(commands.selectById(first.getCommandId())).thenReturn(failedCommand(first));
        when(commands.selectById(second.getCommandId())).thenReturn(failedCommand(second));
        when(commands.selectById(third.getCommandId())).thenReturn(failedCommand(third));
        when(mapper.closeCommandSession(eq(first.getSessionId()), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(mapper.closeCommandSession(eq(second.getSessionId()), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("candidate two failed")).thenReturn(1);
        when(mapper.closeCommandSession(eq(third.getSessionId()), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(lifecycle.closePersistedSessionConditionally(any(String.class), any()))
                .thenAnswer(TerminalCommandResultListenerTest::runConditional);
        AtomicInteger transactions = new AtomicInteger();
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
        TerminalCommandResultListener listener = new TerminalCommandResultListener(mapper, commands,
                lifecycle, audit, Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC),
                transactionOperations);

        listener.reconcileMissed();
        listener.reconcileMissed();

        assertEquals(4, transactions.get());
        verify(mapper, org.mockito.Mockito.times(1)).closeCommandSession(eq(first.getSessionId()),
                any(), any(), any(), any(), any(), any());
        verify(mapper, org.mockito.Mockito.times(2)).closeCommandSession(eq(second.getSessionId()),
                any(), any(), any(), any(), any(), any());
        verify(mapper, org.mockito.Mockito.times(1)).closeCommandSession(eq(third.getSessionId()),
                any(), any(), any(), any(), any(), any());
        verify(audit, org.mockito.Mockito.times(1)).recordTerminal(any(String.class), any(String.class),
                any(String.class), any(), eq(first.getAgentId()), eq(first.getSessionId()),
                eq(first.getCommandId()));
        verify(audit, org.mockito.Mockito.times(1)).recordTerminal(any(String.class), any(String.class),
                any(String.class), any(), eq(second.getAgentId()), eq(second.getSessionId()),
                eq(second.getCommandId()));
        verify(audit, org.mockito.Mockito.times(1)).recordTerminal(any(String.class), any(String.class),
                any(String.class), any(), eq(third.getAgentId()), eq(third.getSessionId()),
                eq(third.getCommandId()));
    }

    private static TerminalSessionRecord row() {
        TerminalSessionRecord row = new TerminalSessionRecord();
        row.setSessionId("33333333-3333-4333-8333-333333333333");
        row.setCommandId("22222222-2222-4222-8222-222222222222");
        row.setAgentId("11111111-1111-4111-8111-111111111111");
        row.setState("WAITING_AGENT");
        return row;
    }

    private static TerminalSessionRecord row(String sessionId, String commandId) {
        TerminalSessionRecord row = row();
        row.setSessionId(sessionId);
        row.setCommandId(commandId);
        return row;
    }

    private static ProcessingAgentCommandRecord failedCommand(TerminalSessionRecord row) {
        ProcessingAgentCommandRecord command = new ProcessingAgentCommandRecord();
        command.setCommandId(row.getCommandId());
        command.setAgentId(row.getAgentId());
        command.setCommandType("OPEN_ROOT_TERMINAL");
        command.setState("FAILED");
        command.setResultCode("PTY_START_FAILED");
        return command;
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

    private static java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(Instant.parse("2026-08-19T12:00:00Z"),
                ZoneOffset.UTC);
    }
}
