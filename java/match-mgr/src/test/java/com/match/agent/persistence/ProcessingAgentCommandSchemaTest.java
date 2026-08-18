package com.match.agent.persistence;

import org.junit.Test;

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
