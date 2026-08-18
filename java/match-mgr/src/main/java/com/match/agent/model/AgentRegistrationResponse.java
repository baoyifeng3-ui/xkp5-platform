package com.match.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentRegistrationResponse {
    private String agentId;
    private String credential;
}
