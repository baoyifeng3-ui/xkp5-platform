package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_training_assignment")
public class UserTrainingAssignment {
    @TableId(value = "assignment_id", type = IdType.AUTO)
    private Integer assignmentId;
    private Integer userId;
    private Integer trainingNodeId;
    private Integer slotNo;
}
