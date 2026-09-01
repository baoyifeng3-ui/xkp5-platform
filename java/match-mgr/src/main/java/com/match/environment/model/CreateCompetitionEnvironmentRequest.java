package com.match.environment.model;

import lombok.Data;

@Data
public class CreateCompetitionEnvironmentRequest {
    private String agentId;
    private Integer slotNumber;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
    private Integer annotationHostPort;
    private Integer editorVscodeHostPort;
    private Integer editorJupyterHostPort;
    private Integer editorT100HostPort;
}
