package com.match.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AgentOperationGrant {
    private boolean allowed;
    private Instant expiresAt;
}
