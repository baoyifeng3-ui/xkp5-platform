package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.ProcessingAgentView;
import com.match.agent.persistence.AgentMetricMinuteMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T13:00:00Z");

    @Test
    public void derivesOnlineStateFromServerTimeWithInclusiveFifteenSecondBoundary() {
        ProcessingAgentMapper agents = mock(ProcessingAgentMapper.class);
        AgentMetricMinuteMapper metrics = mock(AgentMetricMinuteMapper.class);
        ProcessingAgentRecord boundary = agent("a", NOW.minusSeconds(15));
        ProcessingAgentRecord stale = agent("b", NOW.minusSeconds(16));
        when(agents.selectVisibleAgents()).thenReturn(Arrays.asList(boundary, stale));
        AgentQueryService service = new AgentQueryService(agents, metrics, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        List<ProcessingAgentView> views = service.list();

        assertTrue(views.get(0).isOnline());
        assertFalse(views.get(1).isOnline());
    }

    @Test(expected = IllegalArgumentException.class)
    public void historyRejectsRangesLongerThanThirtyDays() {
        AgentQueryService service = new AgentQueryService(mock(ProcessingAgentMapper.class),
                mock(AgentMetricMinuteMapper.class), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        service.history("a", NOW.minusSeconds(30L * 24 * 60 * 60 + 1), NOW);
    }

    private ProcessingAgentRecord agent(String id, Instant lastSeen) {
        ProcessingAgentRecord record = new ProcessingAgentRecord();
        record.setAgentId(id);
        record.setEnabled(true);
        record.setLastSeenAt(LocalDateTime.ofInstant(lastSeen, ZoneOffset.UTC));
        record.setLatestMetrics("{\"cpuPercent\":25.5,\"gpuPercent\":null}");
        return record;
    }
}
