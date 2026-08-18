package com.match.agent.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AgentMetricPoint {
    private LocalDateTime bucketStart;
    private BigDecimal cpuAverage;
    private BigDecimal cpuMax;
    private BigDecimal ramAverage;
    private BigDecimal ramMax;
    private BigDecimal gpuAverage;
    private BigDecimal gpuMax;
    private BigDecimal gpuMemoryAverage;
    private BigDecimal gpuMemoryMax;
    private BigDecimal systemDiskAverage;
    private BigDecimal systemDiskMax;
    private BigDecimal workspaceDiskAverage;
    private BigDecimal workspaceDiskMax;
}
