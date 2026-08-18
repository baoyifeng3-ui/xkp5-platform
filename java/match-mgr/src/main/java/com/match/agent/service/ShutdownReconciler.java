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
    private final Clock clock;

    public ShutdownReconciler(ProcessingAgentCommandMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${xkp.agent.shutdown-reconcile-delay-ms:5000}")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime offlineBefore = now.minusSeconds(OFFLINE_CONFIRMATION_SECONDS);
        List<RunningShutdownCandidate> candidates = mapper.selectRunningShutdowns(BATCH_SIZE);
        if (candidates == null) {
            return;
        }
        for (RunningShutdownCandidate candidate : candidates) {
            if (candidate == null || candidate.getCommandId() == null || candidate.getStartedAt() == null) {
                continue;
            }
            if (candidate.getStartedAt().isAfter(offlineBefore)) {
                continue;
            }
            if (candidate.getLastSeenAt() == null || candidate.getLastSeenAt().isBefore(offlineBefore)) {
                mapper.confirmShutdownOffline(candidate.getCommandId(), now);
                continue;
            }
            if (!candidate.getStartedAt().plusSeconds(FAILURE_DEADLINE_SECONDS).isAfter(now)) {
                mapper.failShutdownConfirmation(candidate.getCommandId(), now);
            }
        }
    }
}
