package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("training_node")
public class TrainingNode {
    @TableId(value = "training_node_id", type = IdType.AUTO)
    private Integer trainingNodeId;
    private Integer trainingServerId;
    private Integer nodeNo;
    private String host;
    private Boolean enabled;

    @TableField(exist = false)
    private List<TrainingSlot> slots;
}
