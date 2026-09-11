package com.match.mode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.mode.persistence.ModeTransitionMapper;
import com.match.mode.persistence.ModeTransitionRecord;
import com.match.mode.persistence.ModeTransitionStepMapper;
import com.match.mode.persistence.ModeTransitionStepRecord;
import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ModeTransitionReconciler {
    private final ModeTransitionStepMapper stepMapper;
    private final ModeTransitionMapper transitionMapper;
    private final ProcessingAgentModeMapper agentModeMapper;
    private final TrainingEnvironmentMapper trainingMapper;
    private final CompetitionEnvironmentMapper competitionMapper;
    private final ModeTransitionService transitionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ModeTransitionReconciler(ModeTransitionStepMapper stepMapper,
                                    ModeTransitionMapper transitionMapper,
                                    ProcessingAgentModeMapper agentModeMapper,
                                    TrainingEnvironmentMapper trainingMapper,
                                    CompetitionEnvironmentMapper competitionMapper,
                                    ModeTransitionService transitionService,
                                    ObjectMapper objectMapper, Clock clock) {
        this.stepMapper = stepMapper;
        this.transitionMapper = transitionMapper;
        this.agentModeMapper = agentModeMapper;
        this.trainingMapper = trainingMapper;
        this.competitionMapper = competitionMapper;
        this.transitionService = transitionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ModeTransitionReconciler(ModeTransitionStepMapper stepMapper,
                             ModeTransitionMapper transitionMapper,
                             ProcessingAgentModeMapper agentModeMapper,
                             ModeTransitionService transitionService, Clock clock) {
        this(stepMapper, transitionMapper, agentModeMapper, null, null, transitionService,
                new ObjectMapper(), clock);
    }

    @Transactional
    public boolean reconcileIfPresent(String commandId, boolean success, String code,
                                      String message, String componentJson) {
        ModeTransitionStepRecord step = stepMapper.selectByCommandForUpdate(commandId);
        if (step == null) {
            return false;
        }
        if (terminal(step.getState())) {
            return true;
        }
        LocalDateTime now = utcNow();
        String state = success ? "SUCCEEDED" : "FAILED";
        String boundedMessage = bounded(message);
        stepMapper.markTerminal(step.getStepId(), state, code, boundedMessage,
                componentJson, now);
        step.setState(state);
        step.setResultCode(code);
        step.setResultMessage(boundedMessage);
        step.setComponentResultsJson(componentJson);
        reconcileEnvironment(step, success, componentJson, now);

        ModeTransitionRecord transition = transitionMapper.selectForUpdate(step.getTransitionId());
        if (transition == null) {
            return true;
        }
        if (!success) {
            String failure = boundedMessage == null ? bounded(code) : boundedMessage;
            transitionMapper.updateState(step.getTransitionId(), "DEGRADED", failure, now);
            transition.setState("DEGRADED");
            transition.setFailureSummary(failure);
            ProcessingAgentModeRecord mode = agentModeMapper.selectForUpdate(transition.getAgentId());
            if (mode != null) {
                agentModeMapper.updateTransition(transition.getAgentId(), transition.getTargetMode(),
                        "DEGRADED", transition.getTransitionId(), now);
            }
            return true;
        }
        transitionService.advanceAfterSuccessfulStep(step.getTransitionId());
        return true;
    }

    private void reconcileEnvironment(ModeTransitionStepRecord step, boolean success,
                                      String componentJson, LocalDateTime now) {
        if (trainingMapper == null || competitionMapper == null) {
            return;
        }
        ComponentStates components = componentStates(componentJson);
        String actual = success ? targetState(step.getActionType()) : "DEGRADED";
        if ("TRAINING".equals(step.getEnvironmentKind())) {
            trainingMapper.reconcileModeStep(step.getEnvironmentId(), actual,
                    components.annotation == null ? "UNKNOWN" : components.annotation,
                    components.editor == null ? "UNKNOWN" : components.editor, now);
        } else if ("COMPETITION".equals(step.getEnvironmentKind())) {
            competitionMapper.reconcileModeStep(step.getEnvironmentId(), actual,
                    components.annotation == null ? "UNKNOWN" : components.annotation,
                    components.editor == null ? "UNKNOWN" : components.editor, success ? now : null,
                    success ? componentJson : null, now);
        }
    }

    private ComponentStates componentStates(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ComponentStates(null, null);
        }
        try {
            JsonNode pair = objectMapper.readTree(json).path("pair");
            return new ComponentStates(text(pair.path("annotation").path("state")),
                    text(pair.path("editor").path("state")));
        } catch (IOException ignored) {
            return new ComponentStates(null, null);
        }
    }

    private String targetState(String action) {
        return action != null && action.startsWith("START_") ? "RUNNING" : "STOPPED";
    }

    private String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private boolean terminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private String bounded(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static final class ComponentStates {
        private final String annotation;
        private final String editor;

        private ComponentStates(String annotation, String editor) {
            this.annotation = annotation;
            this.editor = editor;
        }
    }
}
