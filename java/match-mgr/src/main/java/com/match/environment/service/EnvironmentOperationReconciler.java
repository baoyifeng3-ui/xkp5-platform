package com.match.environment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class EnvironmentOperationReconciler {
    private final EnvironmentOperationMapper operationMapper;
    private final TrainingEnvironmentMapper environmentMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EnvironmentOperationReconciler(EnvironmentOperationMapper operationMapper,
                                          TrainingEnvironmentMapper environmentMapper,
                                          ObjectMapper objectMapper, Clock clock) {
        this.operationMapper = operationMapper;
        this.environmentMapper = environmentMapper;
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
        ComponentStates components = componentStates(componentResultsJson);
        String actualState = success ? successState(operation.getOperationType())
                : (components.present ? "DEGRADED" : "ERROR");
        String operationState = success ? "SUCCEEDED" : "FAILED";
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        operationMapper.markTerminal(operation.getOperationId(), operationState, now, resultCode,
                resultMessage, componentResultsJson);
        environmentMapper.reconcileOperation(operation.getEnvironmentId(), operation.getOperationId(),
                actualState, components.annotation, components.editor, now);
    }

    private String successState(String operationType) {
        return "START".equals(operationType) ? "RUNNING" : "STOPPED";
    }

    private boolean isTerminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private ComponentStates componentStates(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ComponentStates(null, null, false);
        }
        try {
            JsonNode pair = objectMapper.readTree(json).path("pair");
            String annotation = text(pair.path("annotation").path("state"));
            String editor = text(pair.path("editor").path("state"));
            return new ComponentStates(annotation, editor, annotation != null || editor != null);
        } catch (IOException exception) {
            return new ComponentStates(null, null, false);
        }
    }

    private String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static final class ComponentStates {
        private final String annotation;
        private final String editor;
        private final boolean present;

        private ComponentStates(String annotation, String editor, boolean present) {
            this.annotation = annotation;
            this.editor = editor;
            this.present = present;
        }
    }
}
