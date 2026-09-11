package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_deployment")
public class ImageDeploymentRecord {
    @TableId(value = "deployment_id", type = IdType.INPUT)
    private String deploymentId;
    private String releaseId;
    private String fileId;
    private String agentId;
    private String componentType;
    private String targetImage;
    private String targetDigest;
    private String previousDigest;
    private String updatePolicy;
    private String state;
    private String activeDeploymentKey;
    private String activeAgentComponentKey;
    private String commandId;
    private String idempotencyKey;
    private String failureCode;
    private String failureMessage;
    private Integer requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
    @TableField(exist = false) private Integer progressPercent;
    @TableField(exist = false) private String progressStage;
    @TableField(exist = false) private Long transferredBytes;
    @TableField(exist = false) private Long totalBytes;
}
