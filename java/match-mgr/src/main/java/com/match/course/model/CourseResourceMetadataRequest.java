package com.match.course.model;

import lombok.Data;

@Data
public class CourseResourceMetadataRequest {
    private String name;
    private String resourceType;
    private String chapterId;
    private String practiceTool;
    private Integer sortOrder;
}
