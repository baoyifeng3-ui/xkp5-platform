package com.match.agent.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class AgentMetricSnapshot {
    private BigDecimal cpuPercent;
    private BigDecimal ramPercent;
    private BigDecimal gpuPercent;
    private BigDecimal gpuMemoryPercent;
    private BigDecimal systemDiskPercent;
    private BigDecimal workspaceDiskPercent;
    private Boolean dockerAvailable;
    private Integer runningEnvironmentCount;
    private Integer runningContainerCount;
    private Map<String, String> collectorErrors;
}
