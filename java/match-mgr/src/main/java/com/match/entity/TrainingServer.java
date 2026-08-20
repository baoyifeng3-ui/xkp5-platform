package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("training_server")
public class TrainingServer {
    @TableId(value = "training_server_id", type = IdType.AUTO)
    private Integer trainingServerId;
    private String serverName;
    private Integer vscodeBasePort;
    private Integer cvatBasePort;
    private Integer t100BasePort;
    private Boolean enabled;

    @TableField(exist = false)
    private List<TrainingNode> nodes;

    @TableField(exist = false)
    private String heartbeatStatus;

    @TableField(exist = false)
    private Long lastHeartbeatAt;
}
