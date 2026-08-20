package com.match.agent.persistence;

import com.match.Application;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProcessingAgentSchemaTest {
    @Test
    public void migrationDefinesAgentIdentityTokensMetricsAndAudit() throws Exception {
        String sql = read("/db/migration/V18__processing_agent_monitoring.sql");

        assertTrue(sql.contains("CREATE TABLE processing_agent"));
        assertTrue(sql.contains("CREATE TABLE processing_agent_registration_token"));
        assertTrue(sql.contains("CREATE TABLE processing_agent_metric_minute"));
        assertTrue(sql.contains("CREATE TABLE processing_agent_audit"));
        assertTrue(sql.contains("UNIQUE KEY uk_processing_agent_machine"));
        assertTrue(sql.contains("UNIQUE KEY uk_agent_metric_minute"));
        assertTrue(sql.contains("removed_at DATETIME(3) NULL"));
    }

    @Test
    public void applicationScansAgentMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.agent.persistence"));
    }

    private String read(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("missing migration " + path, input);
        try (InputStream closeable = input) {
            byte[] bytes = new byte[closeable.available()];
            int read = closeable.read(bytes);
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }
}
