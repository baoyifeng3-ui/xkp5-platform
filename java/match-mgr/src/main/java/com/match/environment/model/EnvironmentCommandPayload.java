package com.match.environment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class EnvironmentCommandPayload {
    private String environmentId;
    private String operationId;
    private String workspaceRelativePath;
    private List<EnvironmentComponentSpec> components;
}
