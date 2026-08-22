package com.match.course.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("course_resource_delivery")
public class CourseDeliveryRecord {
    @TableId(value = "delivery_id", type = IdType.INPUT) private String deliveryId;
    private String resourceId; private Integer userId; private String environmentId; private String digest;
    private String state; private String operationId; private String commandId; private String failureCode;
    private String failureMessage; private LocalDateTime requestedAt; private LocalDateTime completedAt; private LocalDateTime updatedAt;
}
