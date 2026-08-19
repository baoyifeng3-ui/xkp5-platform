package com.match.terminal.model;

import lombok.Data;

import java.time.Instant;

@Data
public class TerminalSessionView {
    private String sessionId;
    private String agentId;
    private String state;
    private Instant requestedAt;
    private Instant agentConnectionDeadline;
    private Instant absoluteExpiresAt;
    private String commandId;
}
