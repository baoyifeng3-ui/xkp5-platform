package com.match.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course_resource")
public class CourseResourceRecord {
    @TableId(value = "resource_id", type = IdType.INPUT) private String resourceId;
    private String courseId; private String resourceType; private String name; private String storageKey;
    private Long contentLength; private String sha256; private String mimeType; private Integer sortOrder;
    private Boolean enabled; private Integer createdBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
