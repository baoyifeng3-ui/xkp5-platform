package com.match.terminal.model;

import lombok.Data;

import java.time.Instant;

@Data
public class TerminalTicketView {
    private String ticket;
    private Instant expiresAt;
}
