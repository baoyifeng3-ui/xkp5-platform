package com.match.agent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent_audit")
public class AgentAuditRecord {
    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;
    private Integer actorUserId;
    private String agentId;
    private String tokenId;
    private String commandId;
    private String action;
    private String result;
    private String reasonCode;
    private String correlationId;
    private LocalDateTime createdAt;
}
