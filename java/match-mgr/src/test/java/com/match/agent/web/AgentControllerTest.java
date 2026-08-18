package com.match.agent.web;

import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.agent.service.AgentHeartbeatService;
import com.match.agent.service.AgentRegistrationService;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentControllerTest {
    @Test
    public void registrationUsesRawAgentProtocolResponse() {
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        AgentHeartbeatService heartbeats = mock(AgentHeartbeatService.class);
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        AgentRegistrationResponse expected = new AgentRegistrationResponse("agent-id", "secret");
        when(registration.register(request)).thenReturn(expected);
        AgentController controller = new AgentController(registration, credentials, heartbeats);

        AgentRegistrationResponse actual = controller.register(request);

        assertSame(expected, actual);
        verify(registration).register(request);
    }

    @Test
    public void heartbeatAndCommandPollAuthenticateAgent() {
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        AgentHeartbeatService heartbeats = mock(AgentHeartbeatService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        AgentHeartbeatRequest request = new AgentHeartbeatRequest();
        AgentHeartbeatAck expected = new AgentHeartbeatAck(true, 5L, 123L);
        when(credentials.authenticate("Bearer secret")).thenReturn(agent);
        when(heartbeats.accept(agent, request)).thenReturn(expected);
        AgentController controller = new AgentController(registration, credentials, heartbeats);

        assertSame(expected, controller.heartbeat("Bearer secret", request));
        AgentCommandPollResponse commands = controller.pollCommands("Bearer secret", 25);

        assertTrue(commands.getCommands().isEmpty());
        verify(credentials, times(2)).authenticate("Bearer secret");
        verify(heartbeats).accept(agent, request);
    }
}
