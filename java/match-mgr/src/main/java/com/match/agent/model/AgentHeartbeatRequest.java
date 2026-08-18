package com.match.agent.model;

import lombok.Data;

@Data
public class AgentHeartbeatRequest {
    private String agentId;
    private String bootId;
    private Long sequence;
    private String agentVersion;
    private AgentMetricSnapshot metrics;
}
