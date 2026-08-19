package com.match.terminal.service;

import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.service.AgentAuditService;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.junit.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.BooleanSupplier;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

public class TerminalCommandResultListenerTest {
    @Test
    public void afterCommitReconciliationStartsANewTransaction() throws Exception {
        Transactional transactional = TerminalCommandResultListener.class
                .getMethod("onFinished", AgentCommandFinishedEvent.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
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
        when(lifecycle.closePersistedSessionIf(eq(row.getSessionId()), any())).thenAnswer(invocation ->
                ((BooleanSupplier) invocation.getArgument(1)).getAsBoolean());

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
        when(lifecycle.closePersistedSessionIf(eq(row.getSessionId()), any())).thenAnswer(invocation ->
                ((BooleanSupplier) invocation.getArgument(1)).getAsBoolean());

        new TerminalCommandResultListener(mapper, commands, lifecycle,
                mock(AgentAuditService.class),
                Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC))
                .reconcileMissed();

        verify(mapper).closeCommandSession(eq(row.getSessionId()), eq(row.getCommandId()),
                eq(row.getAgentId()), eq("FAILED"), eq("PTY_START_FAILED"),
                eq("Terminal PTY command failed"), any());
    }

    private static TerminalSessionRecord row() {
        TerminalSessionRecord row = new TerminalSessionRecord();
        row.setSessionId("33333333-3333-4333-8333-333333333333");
        row.setCommandId("22222222-2222-4222-8222-222222222222");
        row.setAgentId("11111111-1111-4111-8111-111111111111");
        row.setState("WAITING_AGENT");
        return row;
    }

    private static java.time.LocalDateTime now() {
        return java.time.LocalDateTime.ofInstant(Instant.parse("2026-08-19T12:00:00Z"),
                ZoneOffset.UTC);
    }
}
