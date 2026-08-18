package com.match.agent.model;

import lombok.Data;

@Data
public class AgentRegistrationRequest {
    private String token;
    private String displayName;
    private String machineDigest;
    private String hostname;
    private String primaryIp;
    private String macAddress;
    private String agentVersion;
}
