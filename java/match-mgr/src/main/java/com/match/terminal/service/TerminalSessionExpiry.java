package com.match.terminal.service;

import com.match.agent.service.AgentAuditService;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class TerminalSessionExpiry {
    private static final int BATCH_SIZE = 100;
    private final TerminalSessionMapper mapper;
    private final TerminalRelayLifecycle lifecycle;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final TerminalSessionService sessionService;
    private final TransactionOperations transactions;

    public TerminalSessionExpiry(TerminalSessionMapper mapper, TerminalRelayLifecycle lifecycle,
                                 AgentAuditService auditService, Clock clock,
                                 TerminalSessionService sessionService) {
        this(mapper, lifecycle, auditService, clock, sessionService,
                (TransactionOperations) null);
    }

    @Autowired
    public TerminalSessionExpiry(TerminalSessionMapper mapper, TerminalRelayLifecycle lifecycle,
                                 AgentAuditService auditService, Clock clock,
                                 TerminalSessionService sessionService,
                                 PlatformTransactionManager transactionManager) {
        this(mapper, lifecycle, auditService, clock, sessionService,
                requiresNew(transactionManager));
    }

    TerminalSessionExpiry(TerminalSessionMapper mapper, TerminalRelayLifecycle lifecycle,
                          AgentAuditService auditService, Clock clock,
                          TerminalSessionService sessionService,
                          TransactionOperations transactions) {
        this.mapper = mapper;
        this.lifecycle = lifecycle;
        this.auditService = auditService;
        this.clock = clock;
        this.sessionService = sessionService;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${match.terminal.expiry-delay-ms:5000}")
    public void expire() {
        if (sessionService != null && !sessionService.isStartupRecoveryComplete()) {
            return;
        }
        LocalDateTime now = utcNow();
        LocalDateTime idleBefore = now.minusMinutes(10);
        for (TerminalSessionRecord row : mapper.selectExpired(idleBefore, now, BATCH_SIZE)) {
            ExpiryDecision decision = decide(row, now);
            if (transactions == null) {
                expire(row, idleBefore, now, decision);
            } else {
                transactions.execute(status -> {
                    expire(row, idleBefore, now, decision);
                    return null;
                });
            }
        }
    }

    private void expire(TerminalSessionRecord row, LocalDateTime idleBefore, LocalDateTime now,
                        ExpiryDecision decision) {
        lifecycle.closePersistedSessionConditionally(row.getSessionId(), () -> {
            if (mapper.closeExpired(row.getSessionId(), idleBefore, now, decision.state,
                    decision.reason, decision.message) == 1) {
                auditService.recordTerminal("TERMINAL_TIMEOUT", "SUCCESS", decision.reason,
                        row.getRequesterUserId(), row.getAgentId(), row.getSessionId(),
                        row.getCommandId());
                return TerminalRelayLifecycle.ConditionalCloseDecision.PERSISTED;
            }
            return currentDecision(row.getSessionId());
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (sessionService != null) {
            sessionService.beginStartupRecovery();
        }
        LocalDateTime now = utcNow();
        LocalDateTime recoveryStartedAt = now;
        while (true) {
            List<TerminalSessionRecord> rows = mapper.selectRecoverable(recoveryStartedAt, BATCH_SIZE);
            if (rows.isEmpty()) {
                sessionService.completeStartupRecovery();
                return;
            }
            int affected = 0;
            boolean unresolved = false;
            for (TerminalSessionRecord row : rows) {
                TerminalRelayLifecycle.ConditionalCloseResult result =
                        lifecycle.closePersistedSessionConditionally(row.getSessionId(), () -> {
                            if (mapper.recover(row.getSessionId(), recoveryStartedAt, now) == 1) {
                                return TerminalRelayLifecycle.ConditionalCloseDecision.PERSISTED;
                            }
                            return currentDecision(row.getSessionId());
                        });
                if (result == TerminalRelayLifecycle.ConditionalCloseResult.PERSISTED) {
                    affected++;
                    try {
                        auditService.recordTerminal("TERMINAL_RECOVERY", "FAILURE",
                                "MANAGEMENT_RESTARTED", row.getRequesterUserId(), row.getAgentId(),
                                row.getSessionId(), row.getCommandId());
                    } catch (RuntimeException ignored) {
                        // Recovery must keep the attachment gate moving toward a safe open state.
                    }
                } else if (result == TerminalRelayLifecycle.ConditionalCloseResult.KEPT_OPEN
                        || result == TerminalRelayLifecycle.ConditionalCloseResult.LOCAL_ONLY
                        && currentDecision(row.getSessionId())
                        == TerminalRelayLifecycle.ConditionalCloseDecision.KEEP_OPEN) {
                    unresolved = true;
                }
            }
            if (affected == 0) {
                if (mapper.selectRecoverable(recoveryStartedAt, BATCH_SIZE).isEmpty()) {
                    sessionService.completeStartupRecovery();
                    return;
                }
                if (unresolved) {
                    throw new IllegalStateException("Terminal startup recovery made no progress");
                }
            }
        }
    }

    private ExpiryDecision decide(TerminalSessionRecord row, LocalDateTime now) {
        if (row.getAbsoluteExpiresAt() != null && !row.getAbsoluteExpiresAt().isAfter(now)) {
            return new ExpiryDecision("CLOSED", "ABSOLUTE_EXPIRED", "Terminal lifetime expired");
        }
        if ("WAITING_AGENT".equals(row.getState())) {
            return new ExpiryDecision("FAILED", "AGENT_CONNECT_TIMEOUT", "Agent connection timed out");
        }
        if ("WAITING_BROWSER".equals(row.getState())) {
            return new ExpiryDecision("FAILED", "BROWSER_CONNECT_TIMEOUT", "Browser connection timed out");
        }
        return new ExpiryDecision("CLOSED", "IDLE_TIMEOUT", "Terminal idle timeout expired");
    }

    private TerminalRelayLifecycle.ConditionalCloseDecision currentDecision(String sessionId) {
        TerminalSessionRecord current = mapper.selectById(sessionId);
        return current == null || "CLOSED".equals(current.getState())
                || "FAILED".equals(current.getState())
                ? TerminalRelayLifecycle.ConditionalCloseDecision.LOCAL_ONLY
                : TerminalRelayLifecycle.ConditionalCloseDecision.KEEP_OPEN;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static final class ExpiryDecision {
        private final String state;
        private final String reason;
        private final String message;

        private ExpiryDecision(String state, String reason, String message) {
            this.state = state;
            this.reason = reason;
            this.message = message;
        }
    }
}
