package com.match.agent.model;

import lombok.Data;

import java.time.Instant;

@Data
public class AgentHeartbeatRequest {
    private String agentId;
    private String bootId;
    private Long sequence;
    private Instant timestamp;
    private String agentVersion;
    private AgentMetricSnapshot metrics;
}
