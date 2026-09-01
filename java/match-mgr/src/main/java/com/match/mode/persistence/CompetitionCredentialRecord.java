package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("competition_credential")
public class CompetitionCredentialRecord {
    @TableId(value = "credential_id", type = IdType.INPUT) private String credentialId;
    private Integer userId;
    private Long modeGeneration;
    private String originalPasswordValue;
    private String competitionPasswordValue;
    private String encryptedPlainPassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
