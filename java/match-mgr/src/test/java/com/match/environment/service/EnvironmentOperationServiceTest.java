package com.match.environment.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.TrainingEnvironmentOperationView;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.guard.LicenseGuard;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnvironmentOperationServiceTest {
    @Test public void teacherTakesOverWhileStudentEnvironmentIsCreating() {
        TrainingEnvironmentRecord old = environment("student-create", 21, 31, "STOPPED", "CREATING", 1L);
        TrainingEnvironmentRecord target = environment("class", 21, 32, "STOPPED", "STOPPED", 1L);
        when(environmentMapper.selectUserId("class")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Arrays.asList(old, target));
        EnvironmentOperationRecord active = new EnvironmentOperationRecord(); active.setOperationId("old-create"); active.setOperationType("CREATE");
        when(operationMapper.selectActive("student-create")).thenReturn(active);
        assertEquals("WAITING_DEPENDENCY", service.start("class", 9, "ADMIN").getState());
        verify(operationMapper).markTerminal(eq("old-create"), eq("FAILED"), any(LocalDateTime.class), eq("SUPERSEDED_BY_STOP"), any(String.class), isNull());
    }
    @Test public void teacherTakesOverWhileStudentEnvironmentIsStarting() {
        TrainingEnvironmentRecord old = environment("student", 21, 31, "RUNNING", "STARTING", 1L);
        TrainingEnvironmentRecord target = environment("class", 21, 32, "STOPPED", "STOPPED", 1L);
        when(environmentMapper.selectUserId("class")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Arrays.asList(old, target));
        EnvironmentOperationRecord active = new EnvironmentOperationRecord(); active.setOperationId("old-start"); active.setOperationType("START");
        when(operationMapper.selectActive("student")).thenReturn(active);
        assertEquals("WAITING_DEPENDENCY", service.start("class", 9, "ADMIN").getState());
        verify(operationMapper).markTerminal(eq("old-start"), eq("FAILED"), any(LocalDateTime.class), eq("SUPERSEDED_BY_STOP"), any(String.class), isNull());
        verify(commandService).requestEnvironmentCommand(eq(agent), eq("STOP_TRAINING_ENVIRONMENT"), eq("{}"), eq(9), eq("ADMIN"), eq("student:STOP"));
        verify(commandService, never()).requestEnvironmentCommand(eq(agent), eq("START_TRAINING_ENVIRONMENT"), any(), any(), any(), any());
    }
    @Test public void stopSupersedesPendingStartAndQueuesStopAfterIt() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "RUNNING", "STARTING", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));
        EnvironmentOperationRecord start = new EnvironmentOperationRecord(); start.setOperationId("start-op"); start.setOperationType("START"); start.setState("PENDING");
        when(operationMapper.selectActive("env-selected")).thenReturn(start);
        service.stop("env-selected", 9, "ADMIN");
        verify(operationMapper).markTerminal(eq("start-op"),eq("FAILED"),any(LocalDateTime.class),eq("SUPERSEDED_BY_STOP"),any(String.class),isNull());
        verify(commandService).requestEnvironmentCommand(eq(agent),eq("STOP_TRAINING_ENVIRONMENT"),eq("{}"),eq(9),eq("ADMIN"),eq("env-selected:STOP"));
    }
    @Test(expected = IllegalArgumentException.class)
    public void trainingStartCannotStartCompetitionEnvironment() {
        TrainingEnvironmentRecord competition = environment("competition", 21, 31, "STOPPED", "STOPPED", 0L);
        competition.setEnvironmentType("COMPETITION");
        when(environmentMapper.selectUserId("competition")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(competition));
        service.start("competition", 21, "USER");
    }
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private TrainingEnvironmentMapper environmentMapper;
    private EnvironmentOperationMapper operationMapper;
    private ProcessingAgentMapper agentMapper;
    private AgentCommandService commandService;
    private EnvironmentCommandFactory commandFactory;
    private LicenseGuard licenseGuard;
    private EnvironmentOperationService service;
    private ProcessingAgentRecord agent;
    private com.match.mode.persistence.PlatformModeMapper platformModes;
    private com.match.mode.service.ProcessingAgentModeGuard agentModeGuard;

    @Before
    public void setUp() {
        environmentMapper = mock(TrainingEnvironmentMapper.class);
        operationMapper = mock(EnvironmentOperationMapper.class);
        agentMapper = mock(ProcessingAgentMapper.class);
        commandService = mock(AgentCommandService.class);
        commandFactory = mock(EnvironmentCommandFactory.class);
        licenseGuard = mock(LicenseGuard.class);
        platformModes = mock(com.match.mode.persistence.PlatformModeMapper.class);
        com.match.mode.persistence.PlatformModeRecord platform = new com.match.mode.persistence.PlatformModeRecord();
        platform.setMode("TRAINING");
        when(platformModes.selectForUpdate()).thenReturn(platform);
        agentModeGuard = mock(com.match.mode.service.ProcessingAgentModeGuard.class);
        service = new EnvironmentOperationService(environmentMapper, operationMapper, agentMapper,
                commandService, commandFactory, licenseGuard,
                Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC), platformModes, agentModeGuard);
        agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        when(agentMapper.selectForManagement(AGENT_ID)).thenReturn(agent);
        when(commandFactory.createPayloadJson(any(TrainingEnvironmentRecord.class), any(String.class)))
                .thenReturn("{}");
        when(commandFactory.createControlPayloadJson(any(TrainingEnvironmentRecord.class), any(String.class)))
                .thenReturn("{}");
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("command-1");
        when(commandService.requestEnvironmentCommand(any(), any(), any(), any(), any(), any()))
                .thenReturn(command);
        when(environmentMapper.compareAndSetState(any(), any(Long.class), any(), any(), any(),
                any(Integer.class), any(LocalDateTime.class))).thenReturn(1);
    }

    @Test
    public void switchingCourseStopsCurrentEnvironmentBeforeDispatchingSelectedStart() {
        TrainingEnvironmentRecord current = environment("env-current", 21, 31, "RUNNING", "RUNNING", 4L);
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Arrays.asList(current, selected));

        TrainingEnvironmentOperationView view = service.start("env-selected", 9, "ADMIN");

        InOrder stateOrder = inOrder(environmentMapper);
        stateOrder.verify(environmentMapper).compareAndSetState(eq("env-current"), eq(4L),
                eq("STOPPED"), eq("STOPPING"), any(String.class), eq(9), any(LocalDateTime.class));
        stateOrder.verify(environmentMapper).compareAndSetState(eq("env-selected"), eq(7L),
                eq("RUNNING"), eq("WAITING_DEPENDENCY"), any(String.class), eq(9), any(LocalDateTime.class));
        verify(commandService, times(1)).requestEnvironmentCommand(eq(agent),
                eq("STOP_TRAINING_ENVIRONMENT"), eq("{}"), eq(9), eq("ADMIN"),
                eq("env-current:STOP"));
        verify(commandService, never()).requestEnvironmentCommand(eq(agent),
                eq("START_TRAINING_ENVIRONMENT"), any(), any(), any(), any());
        assertEquals("WAITING_DEPENDENCY", view.getState());
        assertNull(view.getCommand());
    }

    @Test
    public void waitingDependencyStartsAfterOtherEnvironmentsStop() {
        TrainingEnvironmentRecord stopped = environment("env-current", 21, 31, "STOPPED", "STOPPED", 5L);
        TrainingEnvironmentRecord waiting = environment("env-selected", 21, 32, "RUNNING", "WAITING_DEPENDENCY", 8L);
        waiting.setCurrentOperationId("operation-waiting");
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId("operation-waiting");
        operation.setEnvironmentId("env-selected");
        operation.setOperationType("START");
        operation.setState("WAITING_DEPENDENCY");
        operation.setActorUserId(9);
        operation.setActorRole("ADMIN");
        when(environmentMapper.selectWaitingDependencies()).thenReturn(Collections.singletonList(waiting));
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Arrays.asList(stopped, waiting));
        when(operationMapper.selectActive("env-selected")).thenReturn(operation);
        when(operationMapper.dispatchWaiting(eq("operation-waiting"), eq("command-1"), any(LocalDateTime.class))).thenReturn(1);
        when(environmentMapper.dispatchWaitingStart(eq("env-selected"), eq("operation-waiting"), eq(9), any(LocalDateTime.class))).thenReturn(1);

        service.dispatchWaitingStarts();

        verify(commandService).requestEnvironmentCommand(eq(agent), eq("START_TRAINING_ENVIRONMENT"),
                eq("{}"), eq(9), eq("ADMIN"), eq("env-selected:START"));
        verify(operationMapper).dispatchWaiting(eq("operation-waiting"), eq("command-1"), any(LocalDateTime.class));
        verify(environmentMapper).dispatchWaitingStart(eq("env-selected"), eq("operation-waiting"), eq(9), any(LocalDateTime.class));
    }

    @Test
    public void startRequiresActiveLicenseAndDispatchesWhenNoDependencyExists() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        TrainingEnvironmentOperationView view = service.start("env-selected", 9, "SUPER_ADMIN");

        verify(licenseGuard).requireActive();
        verify(commandService).requestEnvironmentCommand(eq(agent), eq("START_TRAINING_ENVIRONMENT"),
                eq("{}"), eq(9), eq("SUPER_ADMIN"), eq("env-selected:START"));
        assertEquals("PENDING", view.getState());
        assertEquals("command-1", view.getCommand().getCommandId());
    }

    @Test
    public void stopRemainsAvailableWithoutCheckingLicense() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "RUNNING", "RUNNING", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        service.stop("env-selected", 9, "ADMIN");

        verify(licenseGuard, never()).requireActive();
        verify(commandService).requestEnvironmentCommand(eq(agent), eq("STOP_TRAINING_ENVIRONMENT"),
                eq("{}"), eq(9), eq("ADMIN"), eq("env-selected:STOP"));
    }

    @Test
    public void stopNormalizesDegradedEnvironmentWhenBothComponentsAreAlreadyStoppedOrMissing() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "DEGRADED", 7L);
        selected.setAnnotationContainerState("STOPPED");
        selected.setEditorContainerState("MISSING");
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        service.stop("env-selected", 9, "ADMIN");

        verify(environmentMapper).compareAndSetState(eq("env-selected"), eq(7L), eq("STOPPED"),
                eq("STOPPED"), isNull(), eq(9), any(LocalDateTime.class));
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void systemCanStopEnvironmentAfterLicenseExpires() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "RUNNING", "RUNNING", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        service.stop("env-selected", 0, "SYSTEM");

        verify(licenseGuard, never()).requireActive();
        verify(commandService).requestEnvironmentCommand(eq(agent), eq("STOP_TRAINING_ENVIRONMENT"),
                eq("{}"), eq(0), eq("SYSTEM"), eq("env-selected:STOP"));
    }

    @Test
    public void systemCannotStartEnvironment() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        expectIllegalArgument(() -> service.start("env-selected", 0, "SYSTEM"), "只能停止");

        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void restoreRequiresLicenseAndFinishesStopped() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        service.restore("env-selected", 9, "ADMIN");

        verify(licenseGuard).requireActive();
        verify(environmentMapper).compareAndSetState(eq("env-selected"), eq(7L),
                eq("STOPPED"), eq("RESTORING"), any(String.class), eq(9), any(LocalDateTime.class));
        verify(commandService).requestEnvironmentCommand(eq(agent), eq("RESTORE_TRAINING_ENVIRONMENT"),
                eq("{}"), eq(9), eq("ADMIN"), eq("env-selected:RESTORE"));
    }

    @Test
    public void normalUserCannotOperateAnotherUsersEnvironment() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));

        expectIllegalArgument(() -> service.start("env-selected", 22, "USER"), "自己的");

        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void repeatedActiveOperationReturnsTheSameOperation() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STARTING", 7L);
        when(environmentMapper.selectUserId("env-selected")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));
        EnvironmentOperationRecord active = new EnvironmentOperationRecord();
        active.setOperationId("operation-existing");
        active.setEnvironmentId("env-selected");
        active.setOperationType("START");
        active.setState("PENDING");
        when(operationMapper.selectActive("env-selected")).thenReturn(active);

        TrainingEnvironmentOperationView view = service.start("env-selected", 9, "ADMIN");

        assertEquals("operation-existing", view.getOperationId());
        verify(environmentMapper, never()).compareAndSetState(any(), any(Long.class), any(), any(), any(),
                any(Integer.class), any(LocalDateTime.class));
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void unboundEnvironmentCannotStart() {
        TrainingEnvironmentRecord selected = environment("env-unbound", 21, 32, "STOPPED", "STOPPED", 7L);
        selected.setUserId(null);
        when(environmentMapper.selectUserId("env-unbound")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(selected));
        expectIllegalArgument(() -> service.start("env-unbound", 21, "USER"), "尚未绑定用户");
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void adminDeleteDispatchesAgentRemovalBeforeDeletingDatabaseRecord() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectForUpdate("env-selected")).thenReturn(selected);
        service.delete("env-selected", 9, "ADMIN");
        verify(commandService).requestEnvironmentCommand(eq(agent),
                eq("DELETE_TRAINING_ENVIRONMENT"), eq("{}"), eq(9), eq("ADMIN"),
                eq("env-selected:DELETE"));
        verify(environmentMapper, never()).deleteById("env-selected");
    }

    @Test
    public void adminCanForceDeleteRunningOrFailedEnvironment() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "RUNNING", "RUNNING", 7L);
        when(environmentMapper.selectForUpdate("env-selected")).thenReturn(selected);
        service.delete("env-selected", 9, "ADMIN");
        verify(environmentMapper).compareAndSetState(eq("env-selected"), eq(7L), eq("STOPPED"),
                eq("DELETING"), any(String.class), eq(9), any(LocalDateTime.class));
        verify(commandService).requestEnvironmentCommand(eq(agent),
                eq("DELETE_TRAINING_ENVIRONMENT"), eq("{}"), eq(9), eq("ADMIN"),
                eq("env-selected:DELETE"));
    }

    @Test
    public void repeatedDeleteReturnsSameOperationWithoutCancellingItsCommand() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "DELETING", 7L);
        when(environmentMapper.selectForUpdate("env-selected")).thenReturn(selected);
        EnvironmentOperationRecord pending = new EnvironmentOperationRecord(); pending.setOperationId("deletion");
        pending.setEnvironmentId("env-selected"); pending.setOperationType("DELETE"); pending.setState("PENDING");
        when(operationMapper.selectActive("env-selected")).thenReturn(pending);
        assertEquals("deletion", service.delete("env-selected", 9, "ADMIN").getOperationId());
        verify(commandService, never()).cancelEnvironmentCommands(any(), any());
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void startRejectedWhileAgentIsStillExitingCompetition() {
        TrainingEnvironmentRecord target = environment("target", 21, 32, "STOPPED", "STOPPED", 2L);
        when(environmentMapper.selectUserId("target")).thenReturn(21);
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Collections.singletonList(target));
        org.mockito.Mockito.doThrow(new com.match.mode.service.ModeConflictException("AGENT_MODE_TRANSITION_ACTIVE", "busy"))
                .when(agentModeGuard).requireIdleForBinding(AGENT_ID);
        try { service.start("target", 21, "USER"); throw new AssertionError("started during mode transition"); }
        catch (com.match.mode.service.ModeConflictException expected) {
            verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    public void competitionModeRejectsTrainingStartBeforeLockingEnvironments() {
        com.match.mode.persistence.PlatformModeRecord platform = new com.match.mode.persistence.PlatformModeRecord();
        platform.setMode("COMPETITION");
        when(platformModes.selectForUpdate()).thenReturn(platform);
        try { service.start("target", 21, "USER"); throw new AssertionError("started in competition mode"); }
        catch (com.match.mode.service.ModeConflictException expected) {
            verify(environmentMapper, never()).selectUserId(any());
        }
    }

    @Test
    public void dependencyFailureTerminatesWaitingStartWithoutStartingTarget() {
        assertWaitingFailure("ERROR", LocalDateTime.of(2026, 8, 19, 11, 59, 59), "DEPENDENCY_FAILED");
    }

    @Test
    public void dependencyTimeoutTerminatesWaitingStartWithoutStartingTarget() {
        assertWaitingFailure("STOPPING", LocalDateTime.of(2026, 8, 19, 11, 40, 0), "DEPENDENCY_TIMEOUT");
    }

    private void assertWaitingFailure(String dependencyState, LocalDateTime requestedAt, String code) {
        TrainingEnvironmentRecord old = environment("old", 21, 31, "STOPPED", dependencyState, 1L);
        TrainingEnvironmentRecord target = environment("target", 21, 32, "RUNNING", "WAITING_DEPENDENCY", 2L);
        target.setCurrentOperationId("waiting");
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId("waiting");
        operation.setEnvironmentId("target");
        operation.setOperationType("START");
        operation.setState("WAITING_DEPENDENCY");
        operation.setActorUserId(9);
        operation.setActorRole("ADMIN");
        operation.setRequestedAt(requestedAt);
        when(environmentMapper.selectWaitingDependencies()).thenReturn(Collections.singletonList(target));
        when(environmentMapper.selectUserEnvironmentsForUpdate(21)).thenReturn(Arrays.asList(old, target));
        when(operationMapper.selectActive("target")).thenReturn(operation);
        service.dispatchWaitingStarts();
        verify(operationMapper).markTerminal(eq("waiting"), eq("FAILED"), any(LocalDateTime.class), eq(code), any(String.class), isNull());
        verify(environmentMapper).compareAndSetState(eq("target"), eq(2L), eq("STOPPED"), eq("ERROR"), isNull(), eq(9), any(LocalDateTime.class));
        verify(commandService, never()).requestEnvironmentCommand(any(), any(), any(), any(), any(), any());
    }

    private TrainingEnvironmentRecord environment(String id, int userId, int courseId,
                                                  String desired, String actual, long version) {
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId(id);
        environment.setUserId(userId);
        environment.setCourseId(String.valueOf(courseId));
        environment.setAgentId(AGENT_ID);
        environment.setDesiredState(desired);
        environment.setActualState(actual);
        environment.setLockVersion(version);
        return environment;
    }

    private void expectIllegalArgument(Runnable action, String messageFragment) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(messageFragment));
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
