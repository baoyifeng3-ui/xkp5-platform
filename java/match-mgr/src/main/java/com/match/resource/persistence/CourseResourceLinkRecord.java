package com.match.resource.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course_resource_link")
public class CourseResourceLinkRecord {
    @TableId(value = "link_id", type = IdType.INPUT)
    private String linkId;
    private String courseId;
    private String fileId;
    private String resourceType;
    private String displayName;
    private String chapterId;
    private String practiceTool;
    private Integer sortOrder;
    private Boolean enabled;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
