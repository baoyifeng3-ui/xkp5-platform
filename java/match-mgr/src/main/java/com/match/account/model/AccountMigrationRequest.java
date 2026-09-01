package com.match.account.model;

import lombok.Data;

@Data
public class AccountMigrationRequest {
    private Integer userId;
    private String targetSlotId;
    private boolean migrateData;
}
