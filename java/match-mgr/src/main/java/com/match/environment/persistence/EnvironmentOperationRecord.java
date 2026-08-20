package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("environment_operation")
public class EnvironmentOperationRecord {
    @TableId(value = "operation_id", type = IdType.INPUT)
    private String operationId;
    private String environmentId;
    private String operationType;
    private Integer actorUserId;
    private String actorRole;
    private String commandId;
    private String state;
    private String activeOperationKey;
    private String correlationId;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String resultCode;
    private String resultMessage;
    private String componentResultsJson;
    private LocalDateTime updatedAt;
}
