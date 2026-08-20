package com.match.agent.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RunningShutdownCandidate {
    private String commandId;
    private String agentId;
    private LocalDateTime startedAt;
    private LocalDateTime lastSeenAt;
}
