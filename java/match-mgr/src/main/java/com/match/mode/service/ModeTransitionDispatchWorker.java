package com.match.mode.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.service.EnvironmentCommandFactory;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/** Owns the transaction that atomically creates Agent commands and associates transition steps. */
@Service
public class ModeTransitionDispatchWorker {
    private static final long ONLINE_TIMEOUT_SECONDS = 300L;

    private final ModeTransitionMapper transitionMapper;
    private final ModeTransitionStepMapper stepMapper;
    private final ProcessingAgentModeMapper modeMapper;
    private final ProcessingAgentMapper agentMapper;
    private final TrainingEnvironmentMapper trainingMapper;
    private final CompetitionEnvironmentMapper competitionMapper;
    private final AgentCommandService commandService;
    private final EnvironmentCommandFactory commandFactory;
    private final Clock clock;

    public ModeTransitionDispatchWorker(ModeTransitionMapper transitionMapper,
                                        ModeTransitionStepMapper stepMapper,
                                        ProcessingAgentModeMapper modeMapper,
                                        ProcessingAgentMapper agentMapper,
                                        TrainingEnvironmentMapper trainingMapper,
                                        CompetitionEnvironmentMapper competitionMapper,
                                        AgentCommandService commandService,
                                        EnvironmentCommandFactory commandFactory,
                                        Clock clock) {
        this.transitionMapper = transitionMapper;
        this.stepMapper = stepMapper;
        this.modeMapper = modeMapper;
        this.agentMapper = agentMapper;
        this.trainingMapper = trainingMapper;
        this.competitionMapper = competitionMapper;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchReadyPhase(String transitionId) {
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(transitionId);
        if (transition == null || "SUCCEEDED".equals(transition.getState())) {
            return;
        }
        ProcessingAgentRecord agent = agentMapper.selectForManagement(transition.getAgentId());
        if (!available(agent)) {
            degradeUnavailable(transition);
            return;
        }
        List<ModeTransitionStepRecord> steps = stepMapper.selectByTransition(transitionId);
        dispatchReady(transition,
                steps == null ? Collections.<ModeTransitionStepRecord>emptyList() : steps,
                agent, transition.getActorUserId(), transition.getActorRole());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchPlanned(ModeTransitionRecord transition,
                                List<ModeTransitionStepRecord> steps,
                                ProcessingAgentRecord agent,
                                Integer actorUserId,
                                String actorRole) {
        if (!available(agent)) {
            degradeUnavailable(transition);
            return;
        }
        dispatchReady(transition, steps, agent, actorUserId, actorRole);
    }

    private void dispatchReady(ModeTransitionRecord transition,
                               List<ModeTransitionStepRecord> steps,
                               ProcessingAgentRecord agent,
                               Integer actorUserId,
                               String actorRole) {
        int phase = lowestReadyPhase(steps);
        if (phase == 0) {
            return;
        }
        for (ModeTransitionStepRecord step : steps) {
            if (step.getPhaseNumber() == null || step.getPhaseNumber() != phase
                    || !"PENDING".equals(step.getState())) {
                continue;
            }
            AgentCommandView command;
            try {
                String payload = payload(step);
                command = commandService.requestEnvironmentCommand(agent,
                        step.getActionType(), payload, actorUserId == null ? 0 : actorUserId,
                        actorRole == null ? "ADMIN" : actorRole, step.getIdempotencyKey());
            } catch (RuntimeException failure) {
                throw dispatchFailure(transition, step, failure.getMessage(), failure);
            }
            if (command == null || command.getCommandId() == null) {
                throw dispatchFailure(transition, step, null, null);
            }
            if (stepMapper.markDispatched(step.getStepId(), command.getCommandId(), utcNow()) != 1) {
                throw dispatchFailure(transition, step, "MODE_STEP_DISPATCH_CONFLICT", null);
            }
            step.setCommandId(command.getCommandId());
            step.setState("DISPATCHED");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatchFailed(ModeTransitionDispatchException failure) {
        ModeTransitionRecord transition = transitionMapper.selectForUpdate(failure.getTransitionId());
        ModeTransitionStepRecord step = stepMapper.selectForUpdate(failure.getStepId());
        if (transition == null || step == null
                || !transition.getTransitionId().equals(step.getTransitionId())
                || !"PENDING".equals(step.getState())) {
            return;
        }
        LocalDateTime now = utcNow();
        String message = bounded(failure.getFailureMessage());
        if (stepMapper.markTerminal(step.getStepId(), "FAILED", "COMMAND_DISPATCH_FAILED",
                message, null, now) != 1) {
            throw new IllegalStateException("MODE_STEP_FAILURE_CONFLICT");
        }
        step.setState("FAILED");
        step.setResultCode("COMMAND_DISPATCH_FAILED");
        step.setResultMessage(message);
        transitionMapper.updateState(transition.getTransitionId(), "DEGRADED",
                "COMMAND_DISPATCH_FAILED", now);
        transition.setState("DEGRADED");
        transition.setFailureSummary("COMMAND_DISPATCH_FAILED");
        modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(),
                "DEGRADED", transition.getTransitionId(), now);
    }

    private ModeTransitionDispatchException dispatchFailure(ModeTransitionRecord transition,
                                                              ModeTransitionStepRecord step,
                                                              String message,
                                                              Throwable cause) {
        return new ModeTransitionDispatchException(transition.getTransitionId(), step.getStepId(),
                bounded(message), cause);
    }

    private void degradeUnavailable(ModeTransitionRecord transition) {
        LocalDateTime now = utcNow();
        transitionMapper.updateState(transition.getTransitionId(), "DEGRADED", "AGENT_OFFLINE", now);
        transition.setState("DEGRADED");
        transition.setFailureSummary("AGENT_OFFLINE");
        modeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(),
                "DEGRADED", transition.getTransitionId(), now);
    }

    private boolean available(ProcessingAgentRecord agent) {
        return agent != null && Boolean.TRUE.equals(agent.getEnabled()) && agent.getRemovedAt() == null
                && agent.getLastSeenAt() != null
                && !agent.getLastSeenAt().isBefore(utcNow().minusSeconds(ONLINE_TIMEOUT_SECONDS));
    }

    private String payload(ModeTransitionStepRecord step) {
        if ("TRAINING".equals(step.getEnvironmentKind())) {
            TrainingEnvironmentRecord environment = trainingMapper.selectForUpdate(step.getEnvironmentId());
            if (environment == null) {
                throw new IllegalArgumentException("实训环境不存在");
            }
            return commandFactory.createPayloadJson(environment, step.getStepId());
        }
        CompetitionEnvironmentRecord environment = competitionMapper.selectForUpdate(step.getEnvironmentId());
        if (environment == null) {
            throw new IllegalArgumentException("比赛环境不存在");
        }
        return commandFactory.createPayloadJson(environment, step.getStepId());
    }

    private int lowestReadyPhase(List<ModeTransitionStepRecord> steps) {
        int phase = Integer.MAX_VALUE;
        for (ModeTransitionStepRecord step : steps) {
            if (!"SUCCEEDED".equals(step.getState()) && step.getPhaseNumber() != null) {
                phase = Math.min(phase, step.getPhaseNumber());
            }
        }
        if (phase == Integer.MAX_VALUE) {
            return 0;
        }
        for (ModeTransitionStepRecord step : steps) {
            if (step.getPhaseNumber() != null && step.getPhaseNumber() == phase
                    && "FAILED".equals(step.getState())) {
                return 0;
            }
        }
        return phase;
    }

    private String bounded(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
