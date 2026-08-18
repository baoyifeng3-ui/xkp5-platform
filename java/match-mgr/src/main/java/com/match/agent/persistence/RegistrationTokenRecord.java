package com.match.agent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent_registration_token")
public class RegistrationTokenRecord {
    @TableId(value = "token_id", type = IdType.INPUT)
    private String tokenId;
    private String tokenDigest;
    private String label;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private String registeredAgentId;
}
