package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class EnvironmentOperationReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private EnvironmentOperationMapper operations;
    private TrainingEnvironmentMapper trainingEnvironments;
    private CompetitionEnvironmentMapper competitionEnvironments;
    private EnvironmentPortAllocationMapper portAllocations;
    private EnvironmentOperationReconciler reconciler;

    @Before
    public void setUp() {
        operations = mock(EnvironmentOperationMapper.class);
        trainingEnvironments = mock(TrainingEnvironmentMapper.class);
        competitionEnvironments = mock(CompetitionEnvironmentMapper.class);
        portAllocations = mock(EnvironmentPortAllocationMapper.class);
        reconciler = new EnvironmentOperationReconciler(operations, trainingEnvironments,
                competitionEnvironments, portAllocations,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void successfulStartBecomesRunningOnlyAfterAgentResult() {
        EnvironmentOperationRecord operation = operation("START", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(trainingEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new TrainingEnvironmentRecord());
        String details = "{\"pair\":{\"annotation\":{\"state\":\"RUNNING\"},"
                + "\"editor\":{\"state\":\"RUNNING\"}}}";

        reconciler.reconcile("command-1", true, "ENVIRONMENT_STARTED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "SUCCEEDED", now,
                "ENVIRONMENT_STARTED", "done", details);
        verify(trainingEnvironments).reconcileOperation("environment-1", "operation-1", "RUNNING",
                "RUNNING", "RUNNING", now);
        verify(competitionEnvironments, never()).reconcileOperation(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    public void successfulDeleteRemovesPortsAndEnvironmentAfterAgentResult() {
        EnvironmentOperationRecord operation = operation("DELETE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(trainingEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new TrainingEnvironmentRecord());
        String details = "{\"pair\":{\"annotation\":{\"state\":\"MISSING\"},"
                + "\"editor\":{\"state\":\"MISSING\"}}}";

        reconciler.reconcile("command-1", true, "ENVIRONMENT_DELETED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "SUCCEEDED", now,
                "ENVIRONMENT_DELETED", "done", details);
        verify(portAllocations).deleteByEnvironment("environment-1");
        verify(trainingEnvironments).deleteById("environment-1");
        verify(trainingEnvironments, never()).reconcileOperation(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    public void componentFailureBecomesDegradedAndPreservesDetails() {
        EnvironmentOperationRecord operation = operation("RESTORE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(trainingEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new TrainingEnvironmentRecord());
        String details = "{\"pair\":{\"annotation\":{\"state\":\"STOPPED\"},"
                + "\"editor\":{\"state\":\"MISSING\"}}}";

        reconciler.reconcile("command-1", false, "ENVIRONMENT_EXECUTION_FAILED", "create failed", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(trainingEnvironments).reconcileOperation("environment-1", "operation-1", "DEGRADED",
                "STOPPED", "MISSING", now);
    }

    @Test
    public void failedTrainingResultWithoutComponentDetailsPreservesStoredStates() {
        EnvironmentOperationRecord operation = operation("START", "PENDING");
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setAnnotationContainerState("STOPPED");
        environment.setEditorContainerState("STOPPED");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(trainingEnvironments.selectForUpdate("environment-1")).thenReturn(environment);

        reconciler.reconcile("command-1", false, "ENVIRONMENT_NOT_READY", "not ready", null);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "FAILED", now,
                "ENVIRONMENT_NOT_READY", "not ready", null);
        verify(trainingEnvironments).reconcileOperation("environment-1", "operation-1", "ERROR",
                "STOPPED", "STOPPED", now);
    }

    @Test
    public void repeatedTerminalResultDoesNotOverwriteNewerEnvironmentState() {
        EnvironmentOperationRecord operation = operation("STOP", "SUCCEEDED");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);

        reconciler.reconcile("command-1", true, "ENVIRONMENT_STOPPED", "done", null);

        verify(trainingEnvironments, never()).reconcileOperation(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    public void successfulCompetitionCreatePersistsCompleteStoppedVerification() {
        EnvironmentOperationRecord operation = operation("CREATE", "PENDING");
        CompetitionEnvironmentRecord competition = competitionEnvironment();
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(competitionEnvironments.selectForUpdate("environment-1")).thenReturn(competition);
        String details = stoppedCompetitionPair();

        reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(competitionEnvironments).reconcileOperation("environment-1", "operation-1",
                "STOPPED", "STOPPED", "STOPPED", now, details, now);
        verify(trainingEnvironments, never()).reconcileOperation(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    public void failedCompetitionResultClearsReadinessButRetainsObservedComponentStates() {
        EnvironmentOperationRecord operation = operation("RESTORE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(competitionEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new CompetitionEnvironmentRecord());
        String details = "{\"pair\":{\"annotation\":{\"state\":\"STOPPED\"},"
                + "\"editor\":{\"state\":\"MISSING\"}}}";

        reconciler.reconcile("command-1", false, "ENVIRONMENT_EXECUTION_FAILED", "failed", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(competitionEnvironments).reconcileOperation("environment-1", "operation-1",
                "DEGRADED", "STOPPED", "MISSING", null, null, now);
    }

    @Test
    public void successfulCompetitionResultWithoutCompletePairClearsReadinessAndFailsClosedState() {
        EnvironmentOperationRecord operation = operation("CREATE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(competitionEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new CompetitionEnvironmentRecord());

        reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", null);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "FAILED", now,
                "ENVIRONMENT_CREATED", "done", null);
        verify(competitionEnvironments).reconcileOperation("environment-1", "operation-1",
                "ERROR", null, null, null, null, now);
    }

    @Test
    public void successfulCompetitionResultWithWrongComponentIdentityFailsClosed() {
        EnvironmentOperationRecord operation = operation("CREATE", "PENDING");
        CompetitionEnvironmentRecord competition = competitionEnvironment();
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(competitionEnvironments.selectForUpdate("environment-1")).thenReturn(competition);
        String details = "{\"pair\":{\"annotation\":{\"componentType\":\"EDITOR\","
                + "\"containerName\":\"xkp-comp-11111111-s2-annotation\",\"state\":\"STOPPED\"},"
                + "\"editor\":{\"componentType\":\"EDITOR\","
                + "\"containerName\":\"xkp-comp-11111111-s2-editor\",\"state\":\"STOPPED\"}}}";

        reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(operations).markTerminal("operation-1", "FAILED", now,
                "ENVIRONMENT_CREATED", "done", details);
        verify(competitionEnvironments).reconcileOperation("environment-1", "operation-1",
                "ERROR", "STOPPED", "STOPPED", null, null, now);
    }

    @Test
    public void oversizedCompetitionResultCannotEstablishReadiness() {
        EnvironmentOperationRecord operation = operation("CREATE", "PENDING");
        when(operations.selectByCommandForUpdate("command-1")).thenReturn(operation);
        when(competitionEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new CompetitionEnvironmentRecord());
        String details = "{\"pair\":{\"annotation\":{\"state\":\"STOPPED\"},"
                + "\"editor\":{\"state\":\"STOPPED\"}},\"padding\":\""
                + repeat('x', 17 * 1024) + "\"}";

        reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", details);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(competitionEnvironments).reconcileOperation("environment-1", "operation-1",
                "ERROR", "STOPPED", "STOPPED", null, null, now);
    }

    @Test
    public void missingEnvironmentFailsClosedWithoutTerminalizingOperation() {
        when(operations.selectByCommandForUpdate("command-1"))
                .thenReturn(operation("CREATE", "PENDING"));

        expectIllegalState(
                () -> reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", null));

        verify(operations, never()).markTerminal(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void ambiguousEnvironmentIdentityFailsClosedWithoutUpdatingEitherTable() {
        when(operations.selectByCommandForUpdate("command-1"))
                .thenReturn(operation("CREATE", "PENDING"));
        when(trainingEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new TrainingEnvironmentRecord());
        when(competitionEnvironments.selectForUpdate("environment-1"))
                .thenReturn(new CompetitionEnvironmentRecord());

        expectIllegalState(
                () -> reconciler.reconcile("command-1", true, "ENVIRONMENT_CREATED", "done", null));

        verify(trainingEnvironments, never()).reconcileOperation(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(competitionEnvironments, never()).reconcileOperation(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    private EnvironmentOperationRecord operation(String type, String state) {
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId("operation-1");
        operation.setEnvironmentId("environment-1");
        operation.setOperationType(type);
        operation.setState(state);
        return operation;
    }

    private CompetitionEnvironmentRecord competitionEnvironment() {
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setAnnotationContainerName("xkp-comp-11111111-s2-annotation");
        environment.setEditorContainerName("xkp-comp-11111111-s2-editor");
        return environment;
    }

    private String stoppedCompetitionPair() {
        return "{\"pair\":{\"annotation\":{\"componentType\":\"ANNOTATION\","
                + "\"containerName\":\"xkp-comp-11111111-s2-annotation\",\"state\":\"STOPPED\"},"
                + "\"editor\":{\"componentType\":\"EDITOR\","
                + "\"containerName\":\"xkp-comp-11111111-s2-editor\",\"state\":\"STOPPED\"}}}";
    }

    private void expectIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("expected IllegalStateException");
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
