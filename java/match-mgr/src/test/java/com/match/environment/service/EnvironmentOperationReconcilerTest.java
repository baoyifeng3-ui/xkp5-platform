package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnvironmentOperationReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private EnvironmentOperationMapper operations;
    private TrainingEnvironmentMapper environments;
    private EnvironmentOperationReconciler reconciler;

    @Before
    public void setUp() {
        operations = mock(EnvironmentOperationMapper.class);
        environments = mock(TrainingEnvironmentMapper.class);
        reconciler = new EnvironmentOperationReconciler(operations, environments,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void successfulStartBecomesRunningOnlyAfterAgentResult() {
        EnvironmentOperationRecord operation = operation("START", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        String details = "{\"pair\":{\"annotation\":{\"state\":\"RUNNING\"},"
                + "\"editor\":{\"state\":\"RUNNING\"}}}";

        reconciler.reconcile("command-1", true, "ENVIRONMENT_STARTED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "SUCCEEDED", now,
                "ENVIRONMENT_STARTED", "done", details);
        verify(environments).reconcileOperation("environment-1", "operation-1", "RUNNING",
                "RUNNING", "RUNNING", now);
    }

    @Test
    public void componentFailureBecomesDegradedAndPreservesDetails() {
        EnvironmentOperationRecord operation = operation("RESTORE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        String details = "{\"pair\":{\"annotation\":{\"state\":\"STOPPED\"},"
                + "\"editor\":{\"state\":\"MISSING\"}}}";

        reconciler.reconcile("command-1", false, "ENVIRONMENT_EXECUTION_FAILED", "create failed", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(environments).reconcileOperation("environment-1", "operation-1", "DEGRADED",
                "STOPPED", "MISSING", now);
    }

    @Test
    public void repeatedTerminalResultDoesNotOverwriteNewerEnvironmentState() {
        EnvironmentOperationRecord operation = operation("STOP", "SUCCEEDED");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);

        reconciler.reconcile("command-1", true, "ENVIRONMENT_STOPPED", "done", null);

        verify(environments, never()).reconcileOperation(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    private EnvironmentOperationRecord operation(String type, String state) {
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId("operation-1");
        operation.setEnvironmentId("environment-1");
        operation.setOperationType(type);
        operation.setState(state);
        return operation;
    }
}
