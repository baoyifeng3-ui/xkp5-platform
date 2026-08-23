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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnvironmentOperationServiceTest {
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private TrainingEnvironmentMapper environmentMapper;
    private EnvironmentOperationMapper operationMapper;
    private ProcessingAgentMapper agentMapper;
    private AgentCommandService commandService;
    private EnvironmentCommandFactory commandFactory;
    private LicenseGuard licenseGuard;
    private EnvironmentOperationService service;
    private ProcessingAgentRecord agent;

    @Before
    public void setUp() {
        environmentMapper = mock(TrainingEnvironmentMapper.class);
        operationMapper = mock(EnvironmentOperationMapper.class);
        agentMapper = mock(ProcessingAgentMapper.class);
        commandService = mock(AgentCommandService.class);
        commandFactory = mock(EnvironmentCommandFactory.class);
        licenseGuard = mock(LicenseGuard.class);
        service = new EnvironmentOperationService(environmentMapper, operationMapper, agentMapper,
                commandService, commandFactory, licenseGuard,
                Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC));
        agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        when(agentMapper.selectForManagement(AGENT_ID)).thenReturn(agent);
        when(commandFactory.createPayloadJson(any(TrainingEnvironmentRecord.class), any(String.class)))
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
    public void adminCanDeleteStoppedEnvironment() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "STOPPED", "STOPPED", 7L);
        when(environmentMapper.selectForUpdate("env-selected")).thenReturn(selected);
        service.delete("env-selected", 9, "ADMIN");
        verify(environmentMapper).deleteById("env-selected");
    }

    @Test
    public void runningEnvironmentCannotBeDeleted() {
        TrainingEnvironmentRecord selected = environment("env-selected", 21, 32, "RUNNING", "RUNNING", 7L);
        when(environmentMapper.selectForUpdate("env-selected")).thenReturn(selected);
        expectIllegalArgument(() -> service.delete("env-selected", 9, "ADMIN"), "停止");
        verify(environmentMapper, never()).deleteById("env-selected");
    }

    private TrainingEnvironmentRecord environment(String id, int userId, int courseId,
                                                  String desired, String actual, long version) {
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId(id);
        environment.setUserId(userId);
        environment.setCourseId(courseId);
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
