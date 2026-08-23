package com.match.course.model;

import lombok.Data;

@Data
public class CourseProgressRequest {
    private String resourceId;
    private String progressKind;
    private Integer progressValue;
    private Long currentValue;
    private Long totalValue;
    private String lastPosition;
    private Boolean completed;
}
