package com.match.agent.web;

import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.service.AgentRegistrationService;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentControllerTest {
    @Test
    public void registrationUsesRawAgentProtocolResponse() {
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        AgentRegistrationResponse expected = new AgentRegistrationResponse("agent-id", "secret");
        when(registration.register(request)).thenReturn(expected);
        AgentController controller = new AgentController(registration);

        AgentRegistrationResponse actual = controller.register(request);

        assertSame(expected, actual);
        verify(registration).register(request);
    }
}
