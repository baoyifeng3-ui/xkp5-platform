package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private ProcessingAgentMapper agents;
    private ProcessingAgentCommandMapper commands;
    private AgentAdministrationService service;

    @Before
    public void setUp() {
        agents = mock(ProcessingAgentMapper.class);
        commands = mock(ProcessingAgentCommandMapper.class);
        service = new AgentAdministrationService(agents, commands, mock(AgentAuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void disablingAgentCancelsEveryActiveCommand() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(agents.setEnabled("agent-id", false, now)).thenReturn(1);

        service.setEnabled("agent-id", false, 7);

        verify(commands).cancelActiveForAgent("agent-id", "AGENT_DISABLED", now);
    }

    @Test
    public void removingAgentCancelsEveryActiveCommand() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(agents.softRemove("agent-id", now)).thenReturn(1);

        service.remove("agent-id", 7);

        verify(commands).cancelActiveForAgent("agent-id", "AGENT_REMOVED", now);
    }
}
