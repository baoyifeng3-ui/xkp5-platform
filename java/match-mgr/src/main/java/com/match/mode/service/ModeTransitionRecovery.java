package com.match.mode.service;

import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ModeTransitionRecovery {
    static final int RECOVERY_LIMIT = 100;

    private final ModeTransitionMapper transitionMapper;
    private final ModeTransitionService transitionService;

    public ModeTransitionRecovery(ModeTransitionMapper transitionMapper,
                                  ModeTransitionService transitionService) {
        this.transitionMapper = transitionMapper;
        this.transitionService = transitionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${match.mode.transition-recovery-delay-ms:5000}")
    public void recover() {
        List<ModeTransitionRecord> active = transitionMapper.selectActiveForRecovery(RECOVERY_LIMIT);
        if (active == null) {
            active = Collections.emptyList();
        }
        int count = Math.min(active.size(), RECOVERY_LIMIT);
        for (int i = 0; i < count; i++) {
            ModeTransitionRecord transition = active.get(i);
            if (transition == null || transition.getTransitionId() == null) {
                continue;
            }
            try {
                transitionService.dispatchReadyPhase(transition.getTransitionId());
            } catch (RuntimeException ignored) {
                // A damaged Agent transition must not prevent recovery of later rows.
            }
        }
    }
}
