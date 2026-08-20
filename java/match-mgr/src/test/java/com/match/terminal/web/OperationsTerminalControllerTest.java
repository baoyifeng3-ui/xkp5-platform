package com.match.terminal.web;

import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.security.LoginSession;
import com.match.security.AdminAccessException;
import com.match.service.impl.UserServiceImpl;
import com.match.config.GlobalExceptionHandler;
import com.match.terminal.model.CreateTerminalSessionRequest;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.model.TerminalTicketView;
import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

public class OperationsTerminalControllerTest {
    @Test
    public void exposesExactOperatorEndpointsAndDelegatesWithGuardedActor() throws Exception {
        RoleGuard guard = mock(RoleGuard.class);
        TerminalSessionService service = mock(TerminalSessionService.class);
        User actor = new User();
        actor.setUserId(7);
        when(guard.requireSuperAdmin()).thenReturn(actor);
        OperationsTerminalController controller = new OperationsTerminalController(guard, service);
        CreateTerminalSessionRequest request = new CreateTerminalSessionRequest();
        request.setConfirmation("OPEN_ROOT_TERMINAL");

        controller.create("agent", request);
        controller.view("session");
        controller.browserTicket("session");
        controller.close("session");

        verify(service).create("agent", actor, "OPEN_ROOT_TERMINAL");
        verify(service).view("session", actor);
        verify(service).issueBrowserTicket("session", actor);
        verify(service).close("session", actor);
        assertEquals("/operations", OperationsTerminalController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertPath("create", PostMapping.class, "/processing-agents/{agentId}/terminal-sessions");
        assertPath("view", GetMapping.class, "/terminal-sessions/{sessionId}");
        assertPath("browserTicket", PostMapping.class, "/terminal-sessions/{sessionId}/browser-ticket");
        assertPath("close", DeleteMapping.class, "/terminal-sessions/{sessionId}");
    }

    @Test
    public void adminAndUserReceiveForbiddenFromEveryOperatorEndpoint() {
        for (String role : new String[]{"ADMIN", "USER"}) {
            UserServiceImpl users = mock(UserServiceImpl.class);
            LoginSession login = mock(LoginSession.class);
            User forbidden = new User();
            forbidden.setUserId(8);
            forbidden.setUserName(role.toLowerCase());
            forbidden.setRole(role);
            forbidden.setEnabled(true);
            forbidden.setMustChangePassword(false);
            when(login.loginId()).thenReturn(8);
            when(users.getById(8)).thenReturn(forbidden);
            TerminalSessionService service = mock(TerminalSessionService.class);
            OperationsTerminalController controller = new OperationsTerminalController(
                    new RoleGuard(users, login), service);
            CreateTerminalSessionRequest request = new CreateTerminalSessionRequest();
            request.setConfirmation("OPEN_ROOT_TERMINAL");

            assertControllerForbidden(() -> controller.create("agent", request));
            assertControllerForbidden(() -> controller.view("session"));
            assertControllerForbidden(() -> controller.browserTicket("session"));
            assertControllerForbidden(() -> controller.close("session"));
            verify(service, never()).create(any(String.class), any(User.class), any(String.class));
        }
    }

    private void assertControllerForbidden(Runnable call) {
        try {
            call.run();
        } catch (AdminAccessException exception) {
            ResponseEntity<?> response = new GlobalExceptionHandler().handleAdminAccess(exception);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            return;
        }
        throw new AssertionError("operator endpoint accepted a non-super-admin");
    }

    private void assertPath(String methodName, Class annotation, String expected) throws Exception {
        Method found = null;
        for (Method method : OperationsTerminalController.class.getMethods()) {
            if (method.getName().equals(methodName)) found = method;
        }
        Object annotationValue = found.getAnnotation(annotation);
        String[] values;
        if (annotation == PostMapping.class) values = ((PostMapping) annotationValue).value();
        else if (annotation == GetMapping.class) values = ((GetMapping) annotationValue).value();
        else values = ((DeleteMapping) annotationValue).value();
        assertEquals(expected, values[0]);
    }
}
