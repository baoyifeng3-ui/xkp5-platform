package com.match.course.model;

import lombok.Data;

@Data
public class CourseProgressRequest {
    private String resourceId;
    private String progressKind;
    private Integer progressValue;
    private Boolean completed;
}
