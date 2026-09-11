package com.match.mode.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persist plans in the platform transaction; command dispatch waits for its commit. */
@Component
public class PlatformModeTransitionListener {
    private final ModeTransitionService transitionService;
    private final com.match.environment.service.ActiveClassSessionService classSessions;

    public PlatformModeTransitionListener(ModeTransitionService transitionService,
            com.match.environment.service.ActiveClassSessionService classSessions) {
        this.transitionService = transitionService;
        this.classSessions = classSessions;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onModeChanged(PlatformModeChangedEvent event) {
        if (event == null) {
            return;
        }
        transitionService.convergeAll(event.getTargetMode(), event.getActorUserId(), event.getActorRole());
        classSessions.stop(event.getActorUserId());
    }
}
