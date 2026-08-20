package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import com.match.licensing.crypto.Digests;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentCredentialServiceTest {
    @Test
    public void authenticatesEnabledAgentByBearerDigest() {
        ProcessingAgentMapper mapper = mock(ProcessingAgentMapper.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-id");
        agent.setEnabled(true);
        when(mapper.selectByCredentialDigest(Digests.sha256("agent-secret"))).thenReturn(agent);

        ProcessingAgentRecord authenticated = new AgentCredentialService(mapper)
                .authenticate("Bearer agent-secret");

        assertEquals("agent-id", authenticated.getAgentId());
    }

    @Test
    public void rejectsMalformedUnknownAndDisabledCredentials() {
        ProcessingAgentMapper mapper = mock(ProcessingAgentMapper.class);
        AgentCredentialService service = new AgentCredentialService(mapper);
        assertCode("AGENT_UNAUTHORIZED", () -> service.authenticate("agent-secret"));
        assertCode("AGENT_UNAUTHORIZED", () -> service.authenticate("Bearer missing"));

        ProcessingAgentRecord disabled = new ProcessingAgentRecord();
        disabled.setEnabled(false);
        when(mapper.selectByCredentialDigest(Digests.sha256("disabled"))).thenReturn(disabled);
        assertCode("AGENT_DISABLED", () -> service.authenticate("Bearer disabled"));
    }

    private void assertCode(String code, Runnable action) {
        try {
            action.run();
        } catch (AgentProtocolException exception) {
            assertEquals(code, exception.getCode());
            return;
        }
        throw new AssertionError("expected AgentProtocolException");
    }
}
