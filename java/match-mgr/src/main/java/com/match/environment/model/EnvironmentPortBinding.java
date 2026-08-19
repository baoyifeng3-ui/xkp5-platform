package com.match.environment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class EnvironmentPortBinding {
    private Integer containerPort;
    private Integer hostPort;
    private String protocol;
}
