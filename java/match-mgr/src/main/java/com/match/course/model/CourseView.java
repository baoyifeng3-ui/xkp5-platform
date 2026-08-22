package com.match.course.model;

import com.match.course.persistence.CourseRecord;
import lombok.Value;

import java.util.List;

@Value
public class CourseView {
    CourseRecord course;
    List<ResourceView> resources;
}
