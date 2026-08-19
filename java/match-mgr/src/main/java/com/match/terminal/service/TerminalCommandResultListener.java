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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionOperations requiresNew;

    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock) {
        this(mapper, null, lifecycle, auditService, clock, null, (TransactionOperations) null);
    }

    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         ProcessingAgentCommandMapper commandMapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock) {
        this(mapper, commandMapper, lifecycle, auditService, clock, null,
                (TransactionOperations) null);
    }

    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         ProcessingAgentCommandMapper commandMapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock,
                                         TransactionOperations requiresNew) {
        this(mapper, commandMapper, lifecycle, auditService, clock, null, requiresNew);
    }

    @Autowired
    public TerminalCommandResultListener(TerminalSessionMapper mapper,
                                         ProcessingAgentCommandMapper commandMapper,
                                         TerminalRelayLifecycle lifecycle,
                                         AgentAuditService auditService, Clock clock,
                                         TerminalSessionService sessionService,
                                         PlatformTransactionManager transactionManager) {
        this(mapper, commandMapper, lifecycle, auditService, clock, sessionService,
                requiresNew(transactionManager));
    }

    private TerminalCommandResultListener(TerminalSessionMapper mapper,
                                          ProcessingAgentCommandMapper commandMapper,
                                          TerminalRelayLifecycle lifecycle,
                                          AgentAuditService auditService, Clock clock,
                                          TerminalSessionService sessionService,
                                          TransactionOperations requiresNew) {
        this.mapper = mapper;
        this.commandMapper = commandMapper;
        this.lifecycle = lifecycle;
        this.auditService = auditService;
        this.clock = clock;
        this.sessionService = sessionService;
        this.requiresNew = requiresNew;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AgentCommandFinishedEvent event) {
        reconcile(event);
    }

    private void reconcile(AgentCommandFinishedEvent event) {
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
        lifecycle.closePersistedSessionConditionally(row.getSessionId(), () -> {
            if (mapper.closeCommandSession(row.getSessionId(), event.getCommandId(), event.getAgentId(),
                    state, reason, message, utcNow()) == 1) {
                auditService.recordTerminal(
                        event.isSuccess() ? "TERMINAL_CLOSE" : "TERMINAL_PTY_FAILURE",
                        event.isSuccess() ? "SUCCESS" : "FAILURE", reason,
                        row.getRequesterUserId(), row.getAgentId(), row.getSessionId(),
                        row.getCommandId());
                return TerminalRelayLifecycle.ConditionalCloseDecision.PERSISTED;
            }
            return currentDecision(row.getSessionId());
        });
    }

    @Scheduled(fixedDelayString = "${match.terminal.command-reconcile-delay-ms:5000}")
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
            AgentCommandFinishedEvent event = new AgentCommandFinishedEvent(command.getCommandId(),
                    command.getAgentId(), command.getCommandType(),
                    "SUCCEEDED".equals(command.getState()), command.getResultCode(),
                    command.getResultMessage());
            try {
                if (requiresNew == null) {
                    reconcile(event);
                } else {
                    requiresNew.execute(status -> {
                        reconcile(event);
                        return null;
                    });
                }
            } catch (RuntimeException ignored) {
                // A later bounded scan retries only the candidate that remains non-terminal.
            }
        }
    }

    private TerminalRelayLifecycle.ConditionalCloseDecision currentDecision(String sessionId) {
        TerminalSessionRecord current = mapper.selectById(sessionId);
        return current == null || "CLOSED".equals(current.getState())
                || "FAILED".equals(current.getState())
                ? TerminalRelayLifecycle.ConditionalCloseDecision.LOCAL_ONLY
                : TerminalRelayLifecycle.ConditionalCloseDecision.KEEP_OPEN;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private String safeFailureCode(String code) {
        return code != null && SAFE_CODE.matcher(code).matches() ? code : "PTY_COMMAND_FAILED";
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
