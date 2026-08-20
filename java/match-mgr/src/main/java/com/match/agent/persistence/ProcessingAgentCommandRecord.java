package com.match.agent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent_command")
public class ProcessingAgentCommandRecord {
    @TableId(value = "command_id", type = IdType.INPUT)
    private String commandId;
    private String agentId;
    private String commandType;
    private Integer commandVersion;
    private String payloadJson;
    private String state;
    private String activeDedupKey;
    private Integer requesterUserId;
    private String requesterRole;
    private String correlationId;
    private LocalDateTime requestedAt;
    private LocalDateTime availableAt;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Integer attemptCount;
    private LocalDateTime deliveredAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String resultCode;
    private String resultMessage;
    private String resultJson;
    private LocalDateTime updatedAt;
}
