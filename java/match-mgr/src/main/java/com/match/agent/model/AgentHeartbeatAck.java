package com.match.agent.model;

import lombok.Data;

@Data
public class AgentHeartbeatAck {
    private boolean accepted;
    private long sequence;
    private long serverTime;
    private AgentOperationGrant operationGrant;

    public AgentHeartbeatAck(boolean accepted, long sequence, long serverTime) {
        this(accepted, sequence, serverTime, null);
    }

    public AgentHeartbeatAck(boolean accepted, long sequence, long serverTime,
                             AgentOperationGrant operationGrant) {
        this.accepted = accepted;
        this.sequence = sequence;
        this.serverTime = serverTime;
        this.operationGrant = operationGrant;
    }
}
