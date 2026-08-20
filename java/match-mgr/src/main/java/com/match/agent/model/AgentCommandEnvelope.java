package com.match.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.Instant;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class AgentCommandEnvelope {
    private String commandId;
    private String type;
    private Integer version;
    private String leaseToken;
    private Instant leaseExpiresAt;
    private JsonNode payload;
}
