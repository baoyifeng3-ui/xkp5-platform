package com.match.agent.web;

import com.match.terminal.service.TerminalSessionException;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AgentProtocolExceptionHandlerTest {
    @Test
    public void takesPrecedenceOverGlobalOperatorAdvice() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
                AgentProtocolExceptionHandler.class.getAnnotation(Order.class).value());
    }

    @Test
    public void returnsStableStatusCodeAndMessage() {
        AgentProtocolException exception = new AgentProtocolException(
                "AGENT_DISABLED", "Agent 已停用", HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, String>> response =
                new AgentProtocolExceptionHandler().handle(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("AGENT_DISABLED", response.getBody().get("code"));
        assertEquals("Agent 已停用", response.getBody().get("message"));
    }

    @Test
    public void returnsStableTerminalFailureWithoutSecrets() {
        TerminalSessionException exception = new TerminalSessionException(
                "TERMINAL_TICKET_UNAVAILABLE", "Terminal ticket is unavailable", HttpStatus.CONFLICT);

        ResponseEntity<Map<String, String>> response =
                new AgentProtocolExceptionHandler().handleTerminal(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("TERMINAL_TICKET_UNAVAILABLE", response.getBody().get("code"));
        assertEquals("Terminal ticket is unavailable", response.getBody().get("message"));
    }
}
