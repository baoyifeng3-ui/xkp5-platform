package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mode_transition")
public class ModeTransitionRecord {
    @TableId(value = "transition_id", type = IdType.INPUT)
    private String transitionId;
    private String agentId;
    private String sourceMode;
    private String targetMode;
    private String state;
    private String activeTransitionKey;
    private Integer actorUserId;
    private String actorRole;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String failureSummary;
    private LocalDateTime updatedAt;
}
