package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mode_transition_step")
public class ModeTransitionStepRecord {
    @TableId(value = "step_id", type = IdType.INPUT)
    private String stepId;
    private String transitionId;
    private Integer phaseNumber;
    private Integer stepOrdinal;
    private String environmentKind;
    private String environmentId;
    private String actionType;
    private String state;
    private String commandId;
    private String idempotencyKey;
    private String resultCode;
    private String resultMessage;
    private String componentResultsJson;
    private LocalDateTime updatedAt;
}
