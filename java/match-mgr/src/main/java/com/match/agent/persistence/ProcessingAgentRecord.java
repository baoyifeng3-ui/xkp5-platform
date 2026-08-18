package com.match.agent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent")
public class ProcessingAgentRecord {
    @TableId(value = "agent_id", type = IdType.INPUT)
    private String agentId;
    private String displayName;
    private String machineDigest;
    private String hostname;
    private String primaryIp;
    private String macAddress;
    private String agentVersion;
    private String credentialDigest;
    private Boolean enabled;
    private LocalDateTime lastSeenAt;
    private String lastBootId;
    private Long lastSequence;
    private String latestMetrics;
    private LocalDateTime removedAt;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
