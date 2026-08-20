package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mode_training_snapshot")
public class ModeTrainingSnapshotRecord {
    @TableId(value = "transition_id", type = IdType.INPUT)
    private String transitionId;
    private String environmentId;
    private LocalDateTime capturedAt;
}
