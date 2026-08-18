package com.match.agent.web;

import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentCommandPollResponse;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.agent.service.AgentHeartbeatService;
import com.match.agent.service.AgentRegistrationService;
import com.match.agent.service.AgentCommandPoller;
import com.match.agent.service.AgentCommandService;
import org.junit.Test;

import java.util.Collections;

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
        AgentCommandPoller commandPoller = mock(AgentCommandPoller.class);
        AgentCommandService commands = mock(AgentCommandService.class);
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        AgentRegistrationResponse expected = new AgentRegistrationResponse("agent-id", "secret");
        when(registration.register(request)).thenReturn(expected);
        AgentController controller = new AgentController(registration, credentials, heartbeats,
                commandPoller, commands);

        AgentRegistrationResponse actual = controller.register(request);

        assertSame(expected, actual);
        verify(registration).register(request);
    }

    @Test
    public void heartbeatAndCommandPollAuthenticateAgent() {
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        AgentHeartbeatService heartbeats = mock(AgentHeartbeatService.class);
        AgentCommandPoller commandPoller = mock(AgentCommandPoller.class);
        AgentCommandService commandService = mock(AgentCommandService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        AgentHeartbeatRequest request = new AgentHeartbeatRequest();
        AgentHeartbeatAck expected = new AgentHeartbeatAck(true, 5L, 123L);
        when(credentials.authenticate("Bearer secret")).thenReturn(agent);
        when(heartbeats.accept(agent, request)).thenReturn(expected);
        AgentCommandEnvelope command = new AgentCommandEnvelope();
        command.setCommandId("command-id");
        AgentCommandPollResponse expectedPoll = new AgentCommandPollResponse(Collections.singletonList(command));
        when(commandPoller.poll(agent, 25)).thenReturn(expectedPoll);
        AgentController controller = new AgentController(registration, credentials, heartbeats,
                commandPoller, commandService);

        assertSame(expected, controller.heartbeat("Bearer secret", request));
        AgentCommandPollResponse commands = controller.pollCommands("Bearer secret", 25);

        assertSame(expectedPoll, commands);
        assertSame(command, commands.getCommands().get(0));
        verify(credentials, times(2)).authenticate("Bearer secret");
        verify(heartbeats).accept(agent, request);
        verify(commandPoller).poll(agent, 25);
    }

    @Test
    public void startAndResultUseAuthenticatedAgent() {
        AgentRegistrationService registration = mock(AgentRegistrationService.class);
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        AgentHeartbeatService heartbeats = mock(AgentHeartbeatService.class);
        AgentCommandPoller commandPoller = mock(AgentCommandPoller.class);
        AgentCommandService commandService = mock(AgentCommandService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        AgentCommandStartRequest start = new AgentCommandStartRequest("lease-id");
        AgentCommandResultRequest result = new AgentCommandResultRequest();
        AgentCommandView started = new AgentCommandView();
        AgentCommandView finished = new AgentCommandView();
        when(credentials.authenticate("Bearer secret")).thenReturn(agent);
        when(commandService.start(agent, "command-id", start)).thenReturn(started);
        when(commandService.finish(agent, "command-id", result)).thenReturn(finished);
        AgentController controller = new AgentController(registration, credentials, heartbeats,
                commandPoller, commandService);

        assertSame(started, controller.startCommand("Bearer secret", "command-id", start));
        assertSame(finished, controller.finishCommand("Bearer secret", "command-id", result));

        verify(credentials, times(2)).authenticate("Bearer secret");
        verify(commandService).start(agent, "command-id", start);
        verify(commandService).finish(agent, "command-id", result);
    }

    @Test(expected = AgentProtocolException.class)
    public void invalidPollWaitIsRejectedBeforePolling() {
        AgentController controller = new AgentController(mock(AgentRegistrationService.class),
                mock(AgentCredentialService.class), mock(AgentHeartbeatService.class),
                mock(AgentCommandPoller.class), mock(AgentCommandService.class));

        controller.pollCommands("Bearer secret", 26);
    }
}
