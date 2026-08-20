package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("environment_port_allocation")
public class EnvironmentPortAllocationRecord {
    @TableId(value = "allocation_id", type = IdType.INPUT)
    private String allocationId;
    private String slotId;
    private String agentId;
    private String componentType;
    private Integer containerPort;
    private Integer hostPort;
    private String protocol;
    private LocalDateTime createdAt;
}
