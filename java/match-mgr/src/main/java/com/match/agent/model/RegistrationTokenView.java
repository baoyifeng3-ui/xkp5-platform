package com.match.agent.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegistrationTokenView {
    private String tokenId;
    private String token;
    private String label;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private String registeredAgentId;
}
