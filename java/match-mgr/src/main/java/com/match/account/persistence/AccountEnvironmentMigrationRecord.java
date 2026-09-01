package com.match.account.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account_environment_migration")
public class AccountEnvironmentMigrationRecord {
    @TableId(value = "migration_id", type = IdType.INPUT) private String migrationId;
    private Integer userId;
    private String sourceAgentId;
    private String sourceSlotId;
    private String targetAgentId;
    private String targetSlotId;
    private Boolean migrateData;
    private String state;
    private String stage;
    private String failureCode;
    private String failureMessage;
    private Integer requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
