package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.RunningShutdownCandidate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ShutdownReconciler {
    private static final int BATCH_SIZE = 100;
    private static final long OFFLINE_CONFIRMATION_SECONDS = 15;
    private static final long FAILURE_DEADLINE_SECONDS = 90;

    private final ProcessingAgentCommandMapper mapper;
    private final AgentAuditService auditService;
    private final Clock clock;

    public ShutdownReconciler(ProcessingAgentCommandMapper mapper, AgentAuditService auditService,
                              Clock clock) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${xkp.agent.shutdown-reconcile-delay-ms:5000}")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        recoverExpiredLeases(now);
        LocalDateTime offlineBefore = now.minusSeconds(OFFLINE_CONFIRMATION_SECONDS);
        List<RunningShutdownCandidate> candidates = mapper.selectRunningShutdowns(BATCH_SIZE);
        if (candidates == null) {
            return;
        }
        for (RunningShutdownCandidate candidate : candidates) {
            if (candidate == null || candidate.getCommandId() == null || candidate.getStartedAt() == null) {
                continue;
            }
            LocalDateTime deadline = candidate.getStartedAt().plusSeconds(FAILURE_DEADLINE_SECONDS);
            if (!deadline.isAfter(now)) {
                LocalDateTime deadlineOfflineBefore = deadline.minusSeconds(OFFLINE_CONFIRMATION_SECONDS);
                if (candidate.getLastSeenAt() == null
                        || candidate.getLastSeenAt().isBefore(deadlineOfflineBefore)) {
                    if (mapper.confirmShutdownOffline(candidate.getCommandId(), now) == 1) {
                        auditService.recordCommandSuccess("SHUTDOWN_OFFLINE_CONFIRMED", null,
                                candidate.getAgentId(),
                                candidate.getCommandId());
                    }
                } else {
                    if (mapper.failShutdownConfirmation(candidate.getCommandId(), now) == 1) {
                        auditService.recordCommandFailure("SHUTDOWN_CONFIRMATION", "SHUTDOWN_NOT_CONFIRMED",
                                null, candidate.getAgentId(), candidate.getCommandId());
                    }
                }
                continue;
            }
            if (!candidate.getStartedAt().isAfter(offlineBefore)
                    && (candidate.getLastSeenAt() == null
                    || candidate.getLastSeenAt().isBefore(offlineBefore))) {
                if (mapper.confirmShutdownOffline(candidate.getCommandId(), now) == 1) {
                    auditService.recordCommandSuccess("SHUTDOWN_OFFLINE_CONFIRMED", null,
                            candidate.getAgentId(),
                            candidate.getCommandId());
                }
            }
        }
    }

    private void recoverExpiredLeases(LocalDateTime now) {
        List<com.match.agent.persistence.ProcessingAgentCommandRecord> expired =
                mapper.selectExpiredLeases(now, BATCH_SIZE);
        if (expired == null) {
            return;
        }
        for (com.match.agent.persistence.ProcessingAgentCommandRecord command : expired) {
            if (command == null || command.getCommandId() == null) {
                continue;
            }
            if (command.getAttemptCount() != null
                    && command.getAttemptCount() >= AgentCommandService.MAX_DELIVERY_ATTEMPTS) {
                if (mapper.failExpiredLease(command.getCommandId(), now,
                        AgentCommandService.MAX_DELIVERY_ATTEMPTS) == 1) {
                    auditService.recordCommandFailure("COMMAND_DELIVERY", "DELIVERY_ATTEMPTS_EXHAUSTED",
                            null, command.getAgentId(), command.getCommandId());
                }
            } else {
                if (mapper.requeueExpiredLease(command.getCommandId(), now,
                        AgentCommandService.MAX_DELIVERY_ATTEMPTS) == 1) {
                    auditService.recordCommandFailure("COMMAND_DELIVERY", "LEASE_EXPIRED",
                            null, command.getAgentId(), command.getCommandId());
                }
            }
        }
    }
}
