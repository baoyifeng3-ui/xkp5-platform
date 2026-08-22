package com.match.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course_resource_progress")
public class CourseProgressRecord {
    @TableId(value = "progress_id", type = IdType.INPUT) private String progressId;
    private Integer userId; private String resourceId; private String progressKind;
    private Long currentValue; private Long totalValue; private BigDecimal percent;
    private String lastPosition; private String state; private LocalDateTime completedAt; private LocalDateTime updatedAt;
}
