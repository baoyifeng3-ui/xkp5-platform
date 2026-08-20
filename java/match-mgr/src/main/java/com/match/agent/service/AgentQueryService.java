package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentMetricPoint;
import com.match.agent.model.AgentMetricSnapshot;
import com.match.agent.model.ProcessingAgentView;
import com.match.agent.persistence.AgentMetricMinuteMapper;
import com.match.agent.persistence.AgentMetricMinuteRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentQueryService {
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(15);
    private static final Duration MAX_HISTORY_RANGE = Duration.ofDays(30);

    private final ProcessingAgentMapper agentMapper;
    private final AgentMetricMinuteMapper metricMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentQueryService(ProcessingAgentMapper agentMapper, AgentMetricMinuteMapper metricMapper,
                             ObjectMapper objectMapper, Clock clock) {
        this.agentMapper = agentMapper;
        this.metricMapper = metricMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<ProcessingAgentView> list() {
        List<ProcessingAgentView> views = new ArrayList<>();
        for (ProcessingAgentRecord record : agentMapper.selectVisibleAgents()) {
            views.add(toView(record));
        }
        return views;
    }

    public ProcessingAgentView detail(String agentId) {
        ProcessingAgentRecord record = agentMapper.selectForManagement(agentId);
        if (record == null) {
            throw new IllegalArgumentException("处理服务器不存在");
        }
        return toView(record);
    }

    public List<AgentMetricPoint> history(String agentId, Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)
                || Duration.between(from, to).compareTo(MAX_HISTORY_RANGE) > 0) {
            throw new IllegalArgumentException("历史查询时间范围必须在 30 天以内");
        }
        List<AgentMetricPoint> points = new ArrayList<>();
        for (AgentMetricMinuteRecord record : metricMapper.selectHistory(agentId,
                LocalDateTime.ofInstant(from, ZoneOffset.UTC), LocalDateTime.ofInstant(to, ZoneOffset.UTC))) {
            points.add(toPoint(record));
        }
        return points;
    }

    private ProcessingAgentView toView(ProcessingAgentRecord record) {
        ProcessingAgentView view = new ProcessingAgentView();
        view.setAgentId(record.getAgentId());
        view.setDisplayName(record.getDisplayName());
        view.setHostname(record.getHostname());
        view.setPrimaryIp(record.getPrimaryIp());
        view.setMacAddress(record.getMacAddress());
        view.setAgentVersion(record.getAgentVersion());
        view.setEnabled(Boolean.TRUE.equals(record.getEnabled()));
        view.setLastSeenAt(record.getLastSeenAt());
        if (record.getLastSeenAt() != null && view.isEnabled()) {
            Instant lastSeen = record.getLastSeenAt().toInstant(ZoneOffset.UTC);
            view.setOnline(!lastSeen.isBefore(clock.instant().minus(ONLINE_WINDOW))
                    && !lastSeen.isAfter(clock.instant()));
        }
        view.setLatestMetrics(readMetrics(record.getLatestMetrics()));
        return view;
    }

    private AgentMetricSnapshot readMetrics(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentMetricSnapshot.class);
        } catch (IOException exception) {
            return null;
        }
    }

    private AgentMetricPoint toPoint(AgentMetricMinuteRecord record) {
        AgentMetricPoint point = new AgentMetricPoint();
        point.setBucketStart(record.getBucketStart());
        point.setCpuAverage(average(record.getCpuSum(), record.getCpuSampleCount()));
        point.setCpuMax(record.getCpuMax());
        point.setRamAverage(average(record.getRamSum(), record.getRamSampleCount()));
        point.setRamMax(record.getRamMax());
        point.setGpuAverage(average(record.getGpuSum(), record.getGpuSampleCount()));
        point.setGpuMax(record.getGpuMax());
        point.setGpuMemoryAverage(average(record.getGpuMemorySum(), record.getGpuMemorySampleCount()));
        point.setGpuMemoryMax(record.getGpuMemoryMax());
        point.setSystemDiskAverage(average(record.getSystemDiskSum(), record.getSystemDiskSampleCount()));
        point.setSystemDiskMax(record.getSystemDiskMax());
        point.setWorkspaceDiskAverage(average(record.getWorkspaceDiskSum(), record.getWorkspaceDiskSampleCount()));
        point.setWorkspaceDiskMax(record.getWorkspaceDiskMax());
        return point;
    }

    private BigDecimal average(BigDecimal sum, Integer count) {
        return sum == null || count == null || count == 0 ? null
                : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
