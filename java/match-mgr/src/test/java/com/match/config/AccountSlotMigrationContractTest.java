package com.match.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class AccountSlotMigrationContractTest {
    @Test
    public void migrationDefinesRequiredAccountPlacementTables() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/db/migration/V41__account_slot_environment_management.sql")),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN remark VARCHAR(500)"));
        assertTrue(sql.contains("environment_id VARCHAR(36)"));
        assertTrue(sql.contains("CREATE TABLE account_environment_migration"));
        assertTrue(sql.contains("CREATE TABLE competition_credential"));
        assertTrue(sql.contains("CREATE TABLE environment_port_pool"));
        assertTrue(sql.contains("UNIQUE KEY uk_environment_port_agent_host"));
    }
}
