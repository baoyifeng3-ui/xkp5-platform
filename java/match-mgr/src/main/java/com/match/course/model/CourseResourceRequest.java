package com.match.course.model;

import lombok.Data;

@Data
public class CourseResourceRequest {
    private String resourceType;
    private String name;
    private String storageKey;
    private Long contentLength;
    private String sha256;
    private String mimeType;
    private Integer sortOrder;
}
