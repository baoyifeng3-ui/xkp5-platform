package com.match.agent.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentCommandView {
    private String commandId;
    private String agentId;
    private String type;
    private Integer version;
    private String state;
    private Integer attemptCount;
    private LocalDateTime requestedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String resultCode;
    private String resultMessage;
}
