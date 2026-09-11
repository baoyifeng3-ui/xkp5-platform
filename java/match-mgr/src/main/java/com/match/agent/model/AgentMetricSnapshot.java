package com.match.agent.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@Data
public class AgentMetricSnapshot {
    private BigDecimal cpuPercent;
    private Long ramTotalBytes;
    private Long ramUsedBytes;
    private BigDecimal ramPercent;
    private String gpuModel;
    private BigDecimal gpuPercent;
    private Integer gpuTemperatureCelsius;
    private Long gpuMemoryTotalBytes;
    private Long gpuMemoryUsedBytes;
    private BigDecimal gpuMemoryPercent;
    private Long systemDiskTotalBytes;
    private Long systemDiskUsedBytes;
    private BigDecimal systemDiskPercent;
    private Long workspaceDiskTotalBytes;
    private Long workspaceDiskUsedBytes;
    private BigDecimal workspaceDiskPercent;
    private Boolean dockerAvailable;
    private String dockerVersion;
    private Integer runningEnvironmentCount;
    private Integer runningContainerCount;
    private Long networkReceiveBytesPerSecond;
    private Long networkSendBytesPerSecond;
    private Map<String, String> collectorErrors;
    private List<AgentDockerImageView> images;
    private List<AgentDockerContainerView> containers;
}
