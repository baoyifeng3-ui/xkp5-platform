package com.match.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentHeartbeatAck {
    private boolean accepted;
    private long sequence;
    private long serverTime;
}
