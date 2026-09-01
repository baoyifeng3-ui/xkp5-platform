package com.match.environment.model;

import lombok.Data;

@Data
public class ClassStartRequest {
    private String courseId;
    private String environmentName;
    private String editorTool;
    private Boolean ignoreOffline;
}
