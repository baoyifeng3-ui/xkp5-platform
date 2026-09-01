package com.match.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class CourseRecord {
    @TableId(value = "course_id", type = IdType.INPUT) private String courseId;
    private String name; private String courseType; private String description;
    private String introductionHtml; private String outlineHtml;
    private String coverResourceId; private Boolean enabled; private Integer createdBy;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
