package com.match.environment.model;

import lombok.Data;
import java.util.List;

@Data
public class CreateTrainingEnvironmentRequest {
    private String environmentName;
    private String remark;
    private List<Integer> userIds;
    private List<Integer> slotNumbers;
    private Integer userId;
    private String courseId;
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
    private String environmentType;
}
