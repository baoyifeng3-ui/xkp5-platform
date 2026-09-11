package com.match.agent.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessingAgentView {
    private String agentId;
    private String displayName;
    private String hostname;
    private String primaryIp;
    private String macAddress;
    private String agentVersion;
    private String targetAgentVersion;
    private boolean upgradeAvailable;
    private boolean enabled;
    private boolean online;
    private LocalDateTime lastSeenAt;
    private AgentMetricSnapshot latestMetrics;
}
