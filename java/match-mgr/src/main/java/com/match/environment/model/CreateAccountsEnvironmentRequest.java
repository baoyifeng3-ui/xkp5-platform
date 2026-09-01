package com.match.environment.model;

import lombok.Data;
import java.util.List;

@Data
public class CreateAccountsEnvironmentRequest {
    private String environmentName;
    private String remark;
    private String environmentType;
    private List<Integer> userIds;
    private String courseId;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
}
