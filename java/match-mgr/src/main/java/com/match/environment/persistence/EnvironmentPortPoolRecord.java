package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("environment_port_pool")
public class EnvironmentPortPoolRecord {
    @TableId(value = "pool_id", type = IdType.INPUT) private String poolId;
    private String agentId;
    private String environmentType;
    private String serviceType;
    private Integer rangeStart;
    private Integer rangeEnd;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
}
