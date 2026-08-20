package com.match.agent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("processing_agent_metric_minute")
public class AgentMetricMinuteRecord {
    @TableId(value = "metric_id", type = IdType.AUTO)
    private Long metricId;
    private String agentId;
    private LocalDateTime bucketStart;
    private Integer sampleCount;
    private Integer cpuSampleCount;
    private BigDecimal cpuSum;
    private BigDecimal cpuMax;
    private Integer ramSampleCount;
    private BigDecimal ramSum;
    private BigDecimal ramMax;
    private Integer gpuSampleCount;
    private BigDecimal gpuSum;
    private BigDecimal gpuMax;
    private Integer gpuMemorySampleCount;
    private BigDecimal gpuMemorySum;
    private BigDecimal gpuMemoryMax;
    private Integer systemDiskSampleCount;
    private BigDecimal systemDiskSum;
    private BigDecimal systemDiskMax;
    private Integer workspaceDiskSampleCount;
    private BigDecimal workspaceDiskSum;
    private BigDecimal workspaceDiskMax;
    private Long runningEnvironmentSum;
    private Integer runningEnvironmentMax;
    private Long runningContainerSum;
    private Integer runningContainerMax;
    private Integer dockerAvailableSamples;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
