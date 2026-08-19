package com.match.environment.model;

import com.match.agent.model.AgentCommandView;
import lombok.Data;

@Data
public class TrainingEnvironmentOperationView {
    private String environmentId;
    private String operationId;
    private AgentCommandView command;
    private String state;
}
