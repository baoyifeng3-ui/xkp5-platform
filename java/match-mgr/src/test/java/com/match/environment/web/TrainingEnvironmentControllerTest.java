package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.environment.persistence.ActiveClassSessionRecord;
import com.match.environment.service.ActiveClassSessionService;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import com.match.util.result.ResponseResult;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrainingEnvironmentControllerTest {
    @Test
    public void adminEditorUrlIncludesHostAndPort() {
        RoleGuard guard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        com.match.agent.persistence.ProcessingAgentMapper agents = mock(com.match.agent.persistence.ProcessingAgentMapper.class);
        com.match.environment.persistence.EnvironmentPortAllocationMapper ports = mock(com.match.environment.persistence.EnvironmentPortAllocationMapper.class);
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("env");
        environment.setAgentId("agent");
        com.match.agent.persistence.ProcessingAgentRecord agent = new com.match.agent.persistence.ProcessingAgentRecord();
        agent.setPrimaryIp("192.0.2.10");
        com.match.environment.persistence.EnvironmentPortAllocationRecord port = new com.match.environment.persistence.EnvironmentPortAllocationRecord();
        port.setComponentType("EDITOR");
        port.setContainerPort(9090);
        port.setHostPort(9091);
        when(service.listAll()).thenReturn(Collections.singletonList(environment));
        when(agents.selectForManagement("agent")).thenReturn(agent);
        when(ports.selectByEnvironment("env")).thenReturn(Collections.singletonList(port));
        AdminTrainingEnvironmentController controller = new AdminTrainingEnvironmentController(guard, service);
        controller.setAgentMapper(agents);
        controller.setPortMapper(ports);
        Map<?, ?> row = (Map<?, ?>) ((List<?>) controller.list().getData()).get(0);
        assertEquals("https://192.0.2.10:9091", row.get("editorUrl"));
    }

    @Test
    public void administratorLifecycleActionsUseTheAuthenticatedRole() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        User administrator = user(9);
        when(roleGuard.requireAnyAdmin()).thenReturn(administrator);
        when(roleGuard.roleOf(administrator)).thenReturn(UserRole.ADMIN);
        AdminTrainingEnvironmentController controller =
                new AdminTrainingEnvironmentController(roleGuard, service);

        controller.start("environment-1");
        controller.stop("environment-1");
        controller.restore("environment-1");

        verify(service).start("environment-1", 9, "ADMIN");
        verify(service).stop("environment-1", 9, "ADMIN");
        verify(service).restore("environment-1", 9, "ADMIN");
    }

    @Test
    public void normalUserSurfaceListsAndStartsOnlyForAuthenticatedUser() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        User student = user(21);
        when(roleGuard.requireUser()).thenReturn(student);
        UserTrainingEnvironmentController controller =
                new UserTrainingEnvironmentController(roleGuard, service);

        controller.list(false);
        controller.start("environment-1");

        verify(service).listForUser(21);
        verify(service).start("environment-1", 21, "USER");
    }

    @Test
    public void normalUserEnvironmentIncludesItsAccountPlacement() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        User student = user(21);
        student.setUserName("student21");
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("environment-1");
        environment.setUserId(21);
        environment.setAgentId("agent-1");
        environment.setSlotNumber(3);
        when(roleGuard.requireUser()).thenReturn(student);
        when(service.listForUser(21)).thenReturn(Collections.singletonList(environment));

        UserTrainingEnvironmentController controller =
                new UserTrainingEnvironmentController(roleGuard, service);
        ResponseResult<Object> response = controller.list(false);
        Map<String, Object> row = (Map<String, Object>) ((List<?>) response.getData()).get(0);

        assertEquals(21, row.get("userId"));
        assertEquals("student21", row.get("userName"));
        assertEquals("agent-1", row.get("agentId"));
        assertEquals(3, row.get("slotNumber"));
    }

    @Test
    public void activeCourseAllowsItsMatchingUserEnvironment() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        ActiveClassSessionService classes = mock(ActiveClassSessionService.class);
        User student = user(21);
        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId("environment-1");
        environment.setCourseId("course-1");
        ActiveClassSessionRecord session = new ActiveClassSessionRecord();
        session.setActive(true);
        session.setCourseId("course-1");
        when(roleGuard.requireUser()).thenReturn(student);
        when(service.listForUser(21)).thenReturn(Collections.singletonList(environment));
        when(classes.current()).thenReturn(session);
        UserTrainingEnvironmentController controller = new UserTrainingEnvironmentController(roleGuard, service);
        controller.setClassSessionService(classes);

        controller.start("environment-1");

        verify(service).start("environment-1", 21, "USER");
    }

    private User user(int id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
