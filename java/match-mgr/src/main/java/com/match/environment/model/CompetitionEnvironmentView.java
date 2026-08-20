package com.match.environment.model;

import com.match.agent.model.AgentCommandView;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompetitionEnvironmentView {
    private String environmentId;
    private String operationId;
    private AgentCommandView command;
    private String agentId;
    private String slotId;
    private Integer slotNumber;
    private Integer userId;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String annotationConfigFingerprint;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
    private String editorConfigFingerprint;
    private String workspaceRelativePath;
    private String desiredState;
    private String actualState;
    private String annotationContainerName;
    private String annotationContainerState;
    private String editorContainerName;
    private String editorContainerState;
    private LocalDateTime lastVerifiedAt;
    private String lastComponentResultsJson;
}
