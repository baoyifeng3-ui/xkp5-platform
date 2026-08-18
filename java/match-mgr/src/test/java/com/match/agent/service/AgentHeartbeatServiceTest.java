package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentMetricSnapshot;
import com.match.agent.persistence.AgentMetricMinuteMapper;
import com.match.agent.persistence.AgentMetricMinuteRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentHeartbeatServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T12:03:45Z");
    private ProcessingAgentMapper agentMapper;
    private AgentMetricMinuteMapper metricMapper;
    private AgentHeartbeatService service;
    private ProcessingAgentRecord agent;

    @Before
    public void setUp() {
        agentMapper = mock(ProcessingAgentMapper.class);
        metricMapper = mock(AgentMetricMinuteMapper.class);
        service = new AgentHeartbeatService(agentMapper, metricMapper, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-id");
    }

    @Test
    public void acceptsNewSequenceAndAggregatesAvailableMetrics() {
        when(agentMapper.updateLatestIfNew(eq("agent-id"), eq("11111111-1111-1111-1111-111111111111"),
                eq(1L), any(), eq("0.1.0"), any(), any())).thenReturn(1);

        AgentHeartbeatAck ack = service.accept(agent, heartbeat(1));

        assertTrue(ack.isAccepted());
        assertEquals(1L, ack.getSequence());
        ArgumentCaptor<AgentMetricMinuteRecord> aggregate =
                ArgumentCaptor.forClass(AgentMetricMinuteRecord.class);
        verify(metricMapper).accumulate(aggregate.capture());
        assertEquals(1, aggregate.getValue().getCpuSampleCount().intValue());
        assertEquals(0, aggregate.getValue().getGpuSampleCount().intValue());
        assertEquals(new BigDecimal("25.50"), aggregate.getValue().getCpuSum());
        assertEquals(Instant.parse("2026-08-18T12:03:00Z"),
                aggregate.getValue().getBucketStart().toInstant(ZoneOffset.UTC));
    }

    @Test
    public void duplicateOrOutOfOrderSequenceIsAcknowledgedWithoutAggregation() {
        when(agentMapper.updateLatestIfNew(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(0);

        AgentHeartbeatAck ack = service.accept(agent, heartbeat(1));

        assertFalse(ack.isAccepted());
        verify(metricMapper, never()).accumulate(any());
    }

    @Test
    public void rejectsMetricOutsidePercentageRange() {
        AgentHeartbeatRequest request = heartbeat(1);
        request.getMetrics().setCpuPercent(new BigDecimal("100.01"));

        try {
            service.accept(agent, request);
        } catch (AgentProtocolException exception) {
            assertEquals("INVALID_HEARTBEAT", exception.getCode());
            return;
        }
        throw new AssertionError("expected invalid heartbeat");
    }

    @Test
    public void rejectsNegativeByteMetric() {
        AgentHeartbeatRequest request = heartbeat(1);
        request.getMetrics().setWorkspaceDiskUsedBytes(-1L);

        try {
            service.accept(agent, request);
        } catch (AgentProtocolException exception) {
            assertEquals("INVALID_HEARTBEAT", exception.getCode());
            return;
        }
        throw new AssertionError("expected invalid heartbeat");
    }

    private AgentHeartbeatRequest heartbeat(long sequence) {
        AgentMetricSnapshot metrics = new AgentMetricSnapshot();
        metrics.setCpuPercent(new BigDecimal("25.50"));
        metrics.setRamPercent(new BigDecimal("40.00"));
        metrics.setGpuPercent(null);
        metrics.setGpuMemoryPercent(null);
        metrics.setSystemDiskPercent(new BigDecimal("35.00"));
        metrics.setWorkspaceDiskPercent(new BigDecimal("45.00"));
        metrics.setDockerAvailable(true);
        metrics.setRunningEnvironmentCount(2);
        metrics.setRunningContainerCount(4);
        metrics.setCollectorErrors(Collections.singletonMap("gpu", "GPU_UNAVAILABLE"));
        AgentHeartbeatRequest request = new AgentHeartbeatRequest();
        request.setAgentId("agent-id");
        request.setBootId("11111111-1111-1111-1111-111111111111");
        request.setSequence(sequence);
        request.setAgentVersion("0.1.0");
        request.setMetrics(metrics);
        return request;
    }
}
