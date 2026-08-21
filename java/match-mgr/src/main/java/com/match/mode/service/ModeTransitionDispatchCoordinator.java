package com.match.mode.service;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/** Separates a rolled-back command transaction from durable failure reconciliation. */
@Service
public class ModeTransitionDispatchCoordinator {
    private final ModeTransitionDispatchWorker worker;

    public ModeTransitionDispatchCoordinator(ModeTransitionDispatchWorker worker) {
        this.worker = worker;
    }

    public void dispatchReadyPhase(String transitionId) {
        try {
            worker.dispatchReadyPhase(transitionId);
        } catch (ModeTransitionDispatchException failure) {
            worker.markDispatchFailed(failure);
        }
    }

    public void dispatchPlanned(ModeTransitionRecord transition,
                                List<ModeTransitionStepRecord> steps,
                                ProcessingAgentRecord agent,
                                Integer actorUserId,
                                String actorRole) {
        try {
            worker.dispatchPlanned(transition, steps, agent, actorUserId, actorRole);
        } catch (ModeTransitionDispatchException failure) {
            worker.markDispatchFailed(failure);
        }
    }
}
