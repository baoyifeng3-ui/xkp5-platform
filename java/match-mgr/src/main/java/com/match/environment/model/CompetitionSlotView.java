package com.match.environment.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CompetitionSlotView {
    private String slotId;
    private String agentId;
    private Integer slotNumber;
    private Integer userId;
    private String environmentId;
    private String readiness;
    private String readinessCode;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String annotationContainerName;
    private String annotationContainerState;
    private String annotationConfigFingerprint;
    private List<EnvironmentPortBinding> annotationPorts;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
    private String editorContainerName;
    private String editorContainerState;
    private String editorConfigFingerprint;
    private List<EnvironmentPortBinding> editorPorts;
    private String workspaceRelativePath;
    private String desiredState;
    private String actualState;
    private LocalDateTime lastVerifiedAt;
    private String annotationUrl;
    private String editorUrl;
}
