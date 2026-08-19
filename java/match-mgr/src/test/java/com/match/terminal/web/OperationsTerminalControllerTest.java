package com.match.terminal.web;

import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.terminal.model.CreateTerminalSessionRequest;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.model.TerminalTicketView;
import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
