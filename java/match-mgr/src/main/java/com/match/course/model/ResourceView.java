package com.match.course.model;

import lombok.Value;

@Value
public class ResourceView {
    String resourceId;
    String courseId;
    String resourceType;
    String chapterId;
    String practiceTool;
    String name;
    Long contentLength;
    String mimeType;
    Integer sortOrder;
    Boolean enabled;
}
