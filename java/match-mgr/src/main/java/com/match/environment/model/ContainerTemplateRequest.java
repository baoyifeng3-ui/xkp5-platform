package com.match.environment.model;

import lombok.Data;

import java.util.List;

@Data
public class ContainerTemplateRequest {
    private String releaseId;
    private String templateId;
    private String templateName;
    private String componentType;
    private String imageReference;
    private String runtimeName;
    private String restartPolicy;
    private List<ContainerPortSpec> ports;
    private String mountTarget;
    private List<String> command;
    private String workingDirectory;
    private Integer cpuLimitMillis;
    private Long memoryLimitBytes;
    private Boolean gpuEnabled;
    private Integer gpuComputePercent;
    private Long gpuMemoryLimitBytes;
    private Boolean privileged;
    private Boolean hostNetwork;
}
