package com.match.agent.persistence;

import org.junit.Test;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProcessingAgentCommandSchemaTest {
    @Test
    public void migrationDefinesDurableCommandStateAndIndexes() throws Exception {
        String sql = read("/db/migration/V20__processing_agent_commands.sql");

        assertTrue(sql.contains("CREATE TABLE processing_agent_command"));
        for (String column : new String[]{"command_id", "agent_id", "command_type", "command_version",
                "payload_json", "state", "requester_user_id", "requester_role", "correlation_id",
                "requested_at", "available_at", "lease_token", "lease_expires_at", "attempt_count",
                "delivered_at", "started_at", "completed_at", "result_code", "result_message", "result_json"}) {
            assertTrue("missing column " + column, sql.contains(column));
        }
        assertTrue(sql.contains("KEY idx_agent_command_poll (agent_id, state, available_at)"));
        assertTrue(sql.contains("KEY idx_agent_command_history (agent_id, requested_at)"));
        assertTrue(sql.contains("UNIQUE KEY uk_agent_active_command (active_dedup_key)"));
        assertTrue(sql.contains("result_message VARCHAR(512)"));
        assertTrue(sql.contains("result_json JSON"));
        assertTrue(sql.contains("attempt_count SMALLINT UNSIGNED"));
    }

    @Test
    public void shutdownReconciliationToleratesLegacyCollationDifferences() throws Exception {
        Select select = ProcessingAgentCommandMapper.class
                .getMethod("selectRunningShutdowns", int.class)
                .getAnnotation(Select.class);
        String statement = String.join(" ", select.value());

        assertTrue(statement.contains("BINARY a.agent_id = BINARY c.agent_id"));
    }

    @Test
    public void commandClaimsRecheckLeaseExpiryAndAgentEnabledState() throws Exception {
        String leased = updateStatement("markLeased", String.class, String.class,
                java.time.LocalDateTime.class, java.time.LocalDateTime.class);
        String running = updateStatement("markRunning", String.class, String.class,
                String.class, java.time.LocalDateTime.class);
        String terminal = updateStatement("markTerminal", String.class, String.class,
                String.class, String.class, java.time.LocalDateTime.class,
                String.class, String.class, String.class);

        assertTrue(leased.contains("processing_agent") && leased.contains("enabled = 1")
                && leased.contains("removed_at IS NULL"));
        assertTrue(running.contains("lease_expires_at > #{startedAt}"));
        assertTrue(running.contains("processing_agent") && running.contains("enabled = 1")
                && running.contains("removed_at IS NULL"));
        assertTrue(terminal.contains("processing_agent") && terminal.contains("enabled = 1")
                && terminal.contains("removed_at IS NULL"));
    }

    @Test
    public void commandAuditMigrationKeepsCommandAndTokenIdentitiesSeparate() throws Exception {
        String sql = read("/db/migration/V21__processing_agent_command_audit.sql");

        assertTrue(sql.contains("ADD COLUMN command_id CHAR(36) NULL"));
        assertTrue(sql.contains("idx_agent_audit_command"));
    }

    private String updateStatement(String method, Class<?>... parameterTypes) throws Exception {
        Update update = ProcessingAgentCommandMapper.class.getMethod(method, parameterTypes)
                .getAnnotation(Update.class);
        return String.join(" ", update.value());
    }

    private String read(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("missing migration " + path, input);
        try (InputStream closeable = input) {
            byte[] bytes = new byte[closeable.available()];
            int count = closeable.read(bytes);
            return new String(bytes, 0, count, StandardCharsets.UTF_8);
        }
    }
}
