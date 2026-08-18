package com.match.agent.web;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AgentProtocolExceptionHandlerTest {
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
}
