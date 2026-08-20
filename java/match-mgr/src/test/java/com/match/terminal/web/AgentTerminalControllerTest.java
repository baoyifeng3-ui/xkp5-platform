package com.match.terminal.web;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.terminal.model.AgentTerminalTicketRequest;
import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentTerminalControllerTest {
    @Test
    public void authenticatesBearerAndUsesExactAgentTicketEndpoint() throws Exception {
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        TerminalSessionService service = mock(TerminalSessionService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent");
        when(credentials.authenticate("Bearer credential")).thenReturn(agent);
        AgentTerminalController controller = new AgentTerminalController(credentials, service);
        AgentTerminalTicketRequest request = new AgentTerminalTicketRequest();
        request.setCommandId("command");
        request.setLeaseToken("lease");

        controller.agentTicket("Bearer credential", "session", request);

        verify(credentials).authenticate("Bearer credential");
        verify(service).issueAgentTicket(agent, "session", "command", "lease");
        assertEquals("/agent/v1", AgentTerminalController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        Method endpoint = AgentTerminalController.class.getMethod("agentTicket",
                String.class, String.class, AgentTerminalTicketRequest.class);
        assertEquals("/terminal-sessions/{sessionId}/agent-ticket",
                endpoint.getAnnotation(PostMapping.class).value()[0]);
    }
}
