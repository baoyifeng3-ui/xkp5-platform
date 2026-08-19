package com.match.terminal.service;

import com.match.agent.service.AgentAuditService;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Autowired
    public TerminalSessionExpiry(TerminalSessionMapper mapper, TerminalRelayLifecycle lifecycle,
                                 AgentAuditService auditService, Clock clock,
                                 TerminalSessionService sessionService) {
        this.mapper = mapper;
        this.lifecycle = lifecycle;
        this.auditService = auditService;
        this.clock = clock;
        this.sessionService = sessionService;
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
            boolean closed = lifecycle.closePersistedSessionIf(row.getSessionId(), () ->
                    mapper.closeExpired(row.getSessionId(), idleBefore, now, decision.state,
                            decision.reason, decision.message) == 1);
            if (closed) {
                auditService.recordTerminal("TERMINAL_TIMEOUT", "SUCCESS", decision.reason,
                        row.getRequesterUserId(), row.getAgentId(), row.getSessionId(), row.getCommandId());
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (sessionService != null) {
            sessionService.beginStartupRecovery();
        }
        LocalDateTime now = utcNow();
        while (true) {
            List<TerminalSessionRecord> rows = mapper.selectRecoverable(BATCH_SIZE);
            if (rows.isEmpty()) {
                sessionService.completeStartupRecovery();
                return;
            }
            int affected = 0;
            for (TerminalSessionRecord row : rows) {
                boolean recovered = lifecycle.closePersistedSessionIf(row.getSessionId(),
                        () -> mapper.recover(row.getSessionId(), now) == 1);
                if (recovered) {
                    affected++;
                    try {
                        auditService.recordTerminal("TERMINAL_RECOVERY", "FAILURE",
                                "MANAGEMENT_RESTARTED", row.getRequesterUserId(), row.getAgentId(),
                                row.getSessionId(), row.getCommandId());
                    } catch (RuntimeException ignored) {
                        // Recovery must keep the attachment gate moving toward a safe open state.
                    }
                }
            }
            if (affected == 0) {
                if (mapper.selectRecoverable(BATCH_SIZE).isEmpty()) {
                    sessionService.completeStartupRecovery();
                    return;
                }
                throw new IllegalStateException("Terminal startup recovery made no progress");
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

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
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
