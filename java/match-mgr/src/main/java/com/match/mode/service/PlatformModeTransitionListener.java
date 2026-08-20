package com.match.mode.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts per-Agent convergence only after the authoritative platform row commits. */
@Component
public class PlatformModeTransitionListener {
    private final ModeTransitionService transitionService;

    public PlatformModeTransitionListener(ModeTransitionService transitionService) {
        this.transitionService = transitionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onModeChanged(PlatformModeChangedEvent event) {
        if (event == null) {
            return;
        }
        transitionService.convergeAll(event.getTargetMode(), event.getActorUserId(), event.getActorRole());
    }
}
