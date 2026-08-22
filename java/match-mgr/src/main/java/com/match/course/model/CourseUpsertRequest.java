package com.match.course.model;

import lombok.Data;

@Data
public class CourseUpsertRequest {
    private String name;
    private String courseType;
    private String description;
    private String coverResourceId;
}
