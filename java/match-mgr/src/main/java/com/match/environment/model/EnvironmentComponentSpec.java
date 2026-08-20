package com.match.environment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class EnvironmentComponentSpec {
    private String componentType;
    private String containerName;
    private String configFingerprint;
    private String imageReference;
    private String runtimeName;
    private String restartPolicy;
    private String mountTarget;
    private List<EnvironmentPortBinding> ports;
    private List<String> command;
    private String workingDirectory;
    private Integer cpuLimitMillis;
    private Long memoryLimitBytes;
    private Boolean gpuEnabled;
    private Integer gpuComputePercent;
    private Long gpuMemoryLimitBytes;
}
