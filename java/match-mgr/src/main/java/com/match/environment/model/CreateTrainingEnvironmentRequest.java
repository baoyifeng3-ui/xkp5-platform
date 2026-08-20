package com.match.environment.model;

import lombok.Data;

@Data
public class CreateTrainingEnvironmentRequest {
    private Integer userId;
    private Integer courseId;
    private String agentId;
    private Integer slotNumber;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
}
