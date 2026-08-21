package com.match.environment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class EnvironmentOperationReconciler {
    private final EnvironmentOperationMapper operationMapper;
    private final TrainingEnvironmentMapper environmentMapper;
    private final CompetitionEnvironmentMapper competitionEnvironmentMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EnvironmentOperationReconciler(EnvironmentOperationMapper operationMapper,
                                          TrainingEnvironmentMapper environmentMapper,
                                          CompetitionEnvironmentMapper competitionEnvironmentMapper,
                                          ObjectMapper objectMapper, Clock clock) {
        this.operationMapper = operationMapper;
        this.environmentMapper = environmentMapper;
        this.competitionEnvironmentMapper = competitionEnvironmentMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(String commandId, boolean success, String resultCode,
                          String resultMessage, String componentResultsJson) {
        EnvironmentOperationRecord operation = operationMapper.selectByCommandForUpdate(commandId);
        if (operation == null || isTerminal(operation.getState())) {
            return;
        }
        TrainingEnvironmentRecord training = environmentMapper.selectForUpdate(operation.getEnvironmentId());
        CompetitionEnvironmentRecord competition = competitionEnvironmentMapper.selectForUpdate(
                operation.getEnvironmentId());
        if ((training == null) == (competition == null)) {
            throw new IllegalStateException("Environment identity is missing or ambiguous");
        }
        ComponentStates components = componentStates(componentResultsJson);
        String boundedResult = competition == null ? null : boundedComponentResult(componentResultsJson);
        boolean competitionVerified = competition != null && success
                && boundedResult != null
                && components.completeFor(successState(operation.getOperationType()), competition);
        String actualState = success
                ? (competition == null || competitionVerified
                        ? successState(operation.getOperationType()) : "ERROR")
                : (components.present ? "DEGRADED" : "ERROR");
        String operationState = success && (competition == null || competitionVerified)
                ? "SUCCEEDED" : "FAILED";
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        operationMapper.markTerminal(operation.getOperationId(), operationState, now, resultCode,
                resultMessage, componentResultsJson);
        if (training != null) {
            environmentMapper.reconcileOperation(operation.getEnvironmentId(), operation.getOperationId(),
                    actualState, components.annotation, components.editor, now);
        } else {
            String verifiedResult = competitionVerified ? boundedResult : null;
            LocalDateTime verifiedAt = verifiedResult == null ? null : now;
            competitionEnvironmentMapper.reconcileOperation(operation.getEnvironmentId(),
                    operation.getOperationId(), actualState, components.annotation, components.editor,
                    verifiedAt, verifiedResult, now);
        }
    }

    private String successState(String operationType) {
        return "START".equals(operationType) ? "RUNNING" : "STOPPED";
    }

    private boolean isTerminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private ComponentStates componentStates(String json) {
        if (json == null || json.trim().isEmpty()) {
            return ComponentStates.empty();
        }
        try {
            JsonNode pair = objectMapper.readTree(json).path("pair");
            JsonNode annotationNode = pair.path("annotation");
            JsonNode editorNode = pair.path("editor");
            String annotation = text(annotationNode.path("state"));
            String editor = text(editorNode.path("state"));
            return new ComponentStates(annotation, editor, annotation != null || editor != null,
                    text(annotationNode.path("componentType")),
                    text(annotationNode.path("containerName")),
                    text(editorNode.path("componentType")),
                    text(editorNode.path("containerName")));
        } catch (IOException exception) {
            return ComponentStates.empty();
        }
    }

    private String boundedComponentResult(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return root != null && root.isObject() ? json : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static final class ComponentStates {
        private final String annotation;
        private final String editor;
        private final boolean present;
        private final String annotationType;
        private final String annotationContainerName;
        private final String editorType;
        private final String editorContainerName;

        private ComponentStates(String annotation, String editor, boolean present,
                                String annotationType, String annotationContainerName,
                                String editorType, String editorContainerName) {
            this.annotation = annotation;
            this.editor = editor;
            this.present = present;
            this.annotationType = annotationType;
            this.annotationContainerName = annotationContainerName;
            this.editorType = editorType;
            this.editorContainerName = editorContainerName;
        }

        private static ComponentStates empty() {
            return new ComponentStates(null, null, false, null, null, null, null);
        }

        private boolean completeFor(String state, CompetitionEnvironmentRecord environment) {
            return state.equals(annotation) && state.equals(editor)
                    && "ANNOTATION".equals(annotationType)
                    && Objects.equals(environment.getAnnotationContainerName(), annotationContainerName)
                    && "EDITOR".equals(editorType)
                    && Objects.equals(environment.getEditorContainerName(), editorContainerName);
        }
    }
}
