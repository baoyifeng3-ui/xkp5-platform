package com.match.agent.model;

import lombok.Data;

@Data
public class AgentPackageRequest {
    private String serverIp;
    private String label;
    private String workspace;
}
