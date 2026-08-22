package com.match.mode.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

@Service
public class ModeTransitionRecovery {
    static final int RECOVERY_LIMIT = 100;

    private final ModeTransitionMapper transitionMapper;
    private final ModeTransitionStepMapper stepMapper;
    private final ProcessingAgentCommandMapper commandMapper;
    private final ModeTransitionReconciler reconciler;
    private final ModeTransitionService transitionService;

    @Autowired
    public ModeTransitionRecovery(ModeTransitionMapper transitionMapper,
                                  ModeTransitionStepMapper stepMapper,
                                  ProcessingAgentCommandMapper commandMapper,
                                  ModeTransitionReconciler reconciler,
                                  ModeTransitionService transitionService) {
        this.transitionMapper = transitionMapper;
        this.stepMapper = stepMapper;
        this.commandMapper = commandMapper;
        this.reconciler = reconciler;
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
                reconcileTerminalCommands(transition.getTransitionId());
                transitionService.advanceAfterSuccessfulStep(transition.getTransitionId());
            } catch (RuntimeException ignored) {
                // A damaged Agent transition must not prevent recovery of later rows.
            }
        }
    }

    private void reconcileTerminalCommands(String transitionId) {
        List<ModeTransitionStepRecord> steps = stepMapper.selectByTransition(transitionId);
        if (steps == null) {
            return;
        }
        for (ModeTransitionStepRecord step : steps) {
            if (step == null || !"DISPATCHED".equals(step.getState())
                    || step.getCommandId() == null) {
                continue;
            }
            ProcessingAgentCommandRecord command = commandMapper.selectById(step.getCommandId());
            if (command == null || !("SUCCEEDED".equals(command.getState())
                    || "FAILED".equals(command.getState()))) {
                continue;
            }
            reconciler.reconcileIfPresent(command.getCommandId(),
                    "SUCCEEDED".equals(command.getState()), command.getResultCode(),
                    command.getResultMessage(), command.getResultJson());
        }
    }
}
