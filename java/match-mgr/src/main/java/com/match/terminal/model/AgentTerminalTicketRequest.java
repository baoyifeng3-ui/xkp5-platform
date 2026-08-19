package com.match.terminal.model;

import lombok.Data;

@Data
public class AgentTerminalTicketRequest {
    private String commandId;
    private String leaseToken;
}
