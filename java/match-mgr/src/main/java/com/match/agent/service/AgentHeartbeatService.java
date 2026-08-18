package com.match.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentHeartbeatAck;
import com.match.agent.model.AgentHeartbeatRequest;
import com.match.agent.model.AgentMetricSnapshot;
import com.match.agent.persistence.AgentMetricMinuteMapper;
import com.match.agent.persistence.AgentMetricMinuteRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AgentHeartbeatService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final ProcessingAgentMapper agentMapper;
    private final AgentMetricMinuteMapper metricMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AgentHeartbeatService(ProcessingAgentMapper agentMapper,
                                 AgentMetricMinuteMapper metricMapper,
                                 ObjectMapper objectMapper) {
        this(agentMapper, metricMapper, objectMapper, Clock.systemUTC());
    }

    AgentHeartbeatService(ProcessingAgentMapper agentMapper,
                          AgentMetricMinuteMapper metricMapper,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.agentMapper = agentMapper;
        this.metricMapper = metricMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AgentHeartbeatAck accept(ProcessingAgentRecord agent, AgentHeartbeatRequest request) {
        validate(agent, request);
        Instant instant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        String metricsJson = serialize(request.getMetrics());
        int updated = agentMapper.updateLatestIfNew(agent.getAgentId(), request.getBootId(),
                request.getSequence(), now, request.getAgentVersion(), metricsJson, now);
        if (updated == 0) {
            return new AgentHeartbeatAck(false, request.getSequence(), instant.toEpochMilli());
        }

        metricMapper.accumulate(toMinuteRecord(agent.getAgentId(), request.getMetrics(), instant, now));
        return new AgentHeartbeatAck(true, request.getSequence(), instant.toEpochMilli());
    }

    private AgentMetricMinuteRecord toMinuteRecord(String agentId, AgentMetricSnapshot metrics,
                                                    Instant instant, LocalDateTime now) {
        AgentMetricMinuteRecord record = new AgentMetricMinuteRecord();
        record.setAgentId(agentId);
        record.setBucketStart(LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MINUTES), ZoneOffset.UTC));
        record.setSampleCount(1);
        record.setCpuSampleCount(sampleCount(metrics.getCpuPercent()));
        record.setCpuSum(metrics.getCpuPercent());
        record.setCpuMax(metrics.getCpuPercent());
        record.setRamSampleCount(sampleCount(metrics.getRamPercent()));
        record.setRamSum(metrics.getRamPercent());
        record.setRamMax(metrics.getRamPercent());
        record.setGpuSampleCount(sampleCount(metrics.getGpuPercent()));
        record.setGpuSum(metrics.getGpuPercent());
        record.setGpuMax(metrics.getGpuPercent());
        record.setGpuMemorySampleCount(sampleCount(metrics.getGpuMemoryPercent()));
        record.setGpuMemorySum(metrics.getGpuMemoryPercent());
        record.setGpuMemoryMax(metrics.getGpuMemoryPercent());
        record.setSystemDiskSampleCount(sampleCount(metrics.getSystemDiskPercent()));
        record.setSystemDiskSum(metrics.getSystemDiskPercent());
        record.setSystemDiskMax(metrics.getSystemDiskPercent());
        record.setWorkspaceDiskSampleCount(sampleCount(metrics.getWorkspaceDiskPercent()));
        record.setWorkspaceDiskSum(metrics.getWorkspaceDiskPercent());
        record.setWorkspaceDiskMax(metrics.getWorkspaceDiskPercent());
        int environments = valueOrZero(metrics.getRunningEnvironmentCount());
        int containers = valueOrZero(metrics.getRunningContainerCount());
        record.setRunningEnvironmentSum((long) environments);
        record.setRunningEnvironmentMax(environments);
        record.setRunningContainerSum((long) containers);
        record.setRunningContainerMax(containers);
        record.setDockerAvailableSamples(Boolean.TRUE.equals(metrics.getDockerAvailable()) ? 1 : 0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private void validate(ProcessingAgentRecord agent, AgentHeartbeatRequest request) {
        if (request == null || agent == null || !agent.getAgentId().equals(request.getAgentId())
                || request.getSequence() == null || request.getSequence() <= 0
                || request.getMetrics() == null || request.getAgentVersion() == null
                || request.getAgentVersion().trim().isEmpty() || !validUuid(request.getBootId())) {
            throw invalidHeartbeat();
        }
        AgentMetricSnapshot metrics = request.getMetrics();
        validatePercent(metrics.getCpuPercent());
        validatePercent(metrics.getRamPercent());
        validatePercent(metrics.getGpuPercent());
        validatePercent(metrics.getGpuMemoryPercent());
        validatePercent(metrics.getSystemDiskPercent());
        validatePercent(metrics.getWorkspaceDiskPercent());
        if (negative(metrics.getRamTotalBytes()) || negative(metrics.getRamUsedBytes())
                || negative(metrics.getGpuMemoryTotalBytes()) || negative(metrics.getGpuMemoryUsedBytes())
                || negative(metrics.getSystemDiskTotalBytes()) || negative(metrics.getSystemDiskUsedBytes())
                || negative(metrics.getWorkspaceDiskTotalBytes()) || negative(metrics.getWorkspaceDiskUsedBytes())
                || negative(metrics.getRunningEnvironmentCount()) || negative(metrics.getRunningContainerCount())) {
            throw invalidHeartbeat();
        }
    }

    private void validatePercent(BigDecimal value) {
        if (value != null && (value.compareTo(ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            throw invalidHeartbeat();
        }
    }

    private boolean validUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean negative(Integer value) {
        return value != null && value < 0;
    }

    private boolean negative(Long value) {
        return value != null && value < 0;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int sampleCount(BigDecimal value) {
        return value == null ? 0 : 1;
    }

    private String serialize(AgentMetricSnapshot metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException exception) {
            throw invalidHeartbeat();
        }
    }

    private AgentProtocolException invalidHeartbeat() {
        return new AgentProtocolException("INVALID_HEARTBEAT", "Agent 心跳数据无效",
                HttpStatus.BAD_REQUEST);
    }
}
