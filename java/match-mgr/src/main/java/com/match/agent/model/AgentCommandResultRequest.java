package com.match.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class AgentCommandResultRequest {
    private String leaseToken;
    private Boolean success;
    private String code;
    private String message;
    private JsonNode details;
}
