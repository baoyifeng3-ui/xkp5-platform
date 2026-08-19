package com.match.terminal.service;

import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.service.AgentAuditService;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

@Component
public class TerminalCommandResultListener {
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");
    private final TerminalSessionMapper mapper;
    private final TerminalRelayLifecycle lifecycle;
    private final ProcessingAgentCommandMapper commandMapper;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final TerminalSessionService sessionService;

    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock) {
        this(mapper, null, lifecycle, auditService, clock, null);
    }

    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         ProcessingAgentCommandMapper commandMapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock) {
        this(mapper, commandMapper, lifecycle, auditService, clock, null);
    }

    @Autowired
    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         ProcessingAgentCommandMapper commandMapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock,
                                         TerminalSessionService sessionService) {
        this.mapper = mapper;
        this.commandMapper = commandMapper;
        this.lifecycle = lifecycle;
        this.auditService = auditService;
        this.clock = clock;
        this.sessionService = sessionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AgentCommandFinishedEvent event) {
        if (event == null || !"OPEN_ROOT_TERMINAL".equals(event.getCommandType())) {
            return;
        }
        TerminalSessionRecord row = mapper.selectByCommandId(event.getCommandId());
        if (row == null || !event.getAgentId().equals(row.getAgentId())) {
            return;
        }
        String state = event.isSuccess() ? "CLOSED" : "FAILED";
        String reason = event.isSuccess() ? "PTY_EXITED" : safeFailureCode(event.getResultCode());
        String message = event.isSuccess() ? "Terminal PTY exited" : "Terminal PTY command failed";
        boolean closed = lifecycle.closePersistedSessionIf(row.getSessionId(), () ->
                mapper.closeCommandSession(row.getSessionId(), event.getCommandId(), event.getAgentId(),
                        state, reason, message, utcNow()) == 1);
        if (closed) {
            try {
                auditService.recordTerminal(
                        event.isSuccess() ? "TERMINAL_CLOSE" : "TERMINAL_PTY_FAILURE",
                        event.isSuccess() ? "SUCCESS" : "FAILURE", reason,
                        row.getRequesterUserId(), row.getAgentId(), row.getSessionId(),
                        row.getCommandId());
            } catch (RuntimeException ignored) {
                // The guarded session close must commit even when audit storage is unavailable.
            }
        }
    }

    @Scheduled(fixedDelayString = "${match.terminal.command-reconcile-delay-ms:5000}")
    @Transactional
    public void reconcileMissed() {
        if (commandMapper == null || sessionService != null
                && !sessionService.isStartupRecoveryComplete()) {
            return;
        }
        for (TerminalSessionRecord row : mapper.selectCommandReconciliationCandidates(100)) {
            ProcessingAgentCommandRecord command = commandMapper.selectById(row.getCommandId());
            if (command == null || !"OPEN_ROOT_TERMINAL".equals(command.getCommandType())
                    || !row.getAgentId().equals(command.getAgentId())
                    || !"SUCCEEDED".equals(command.getState())
                    && !"FAILED".equals(command.getState())) {
                continue;
            }
            onFinished(new AgentCommandFinishedEvent(command.getCommandId(), command.getAgentId(),
                    command.getCommandType(), "SUCCEEDED".equals(command.getState()),
                    command.getResultCode(), command.getResultMessage()));
        }
    }

    private String safeFailureCode(String code) {
        return code != null && SAFE_CODE.matcher(code).matches() ? code : "PTY_COMMAND_FAILED";
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
