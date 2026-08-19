package com.match.environment.persistence;

import com.match.Application;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrainingEnvironmentSchemaTest {
    @Test
    public void migrationDefinesTemplatesEnvironmentsPortsAndOperations() throws Exception {
        String sql = read("/db/migration/V22__training_environment_lifecycle.sql");

        assertTrue(sql.contains("CREATE TABLE processing_environment_slot"));
        assertTrue(sql.contains("CREATE TABLE container_template"));
        assertTrue(sql.contains("CREATE TABLE training_environment"));
        assertTrue(sql.contains("CREATE TABLE environment_port_allocation"));
        assertTrue(sql.contains("CREATE TABLE environment_operation"));

        for (String column : new String[]{"template_id", "component_type", "template_version",
                "enabled", "image_reference", "runtime_name", "restart_policy", "ports_json",
                "mount_target", "command_json", "cpu_limit_millis", "memory_limit_bytes",
                "gpu_enabled", "gpu_compute_percent", "gpu_memory_limit_bytes", "published_at"}) {
            assertTrue("missing template column " + column, sql.contains(column));
        }

        for (String column : new String[]{"environment_id", "user_id", "course_id", "agent_id",
                "slot_number", "annotation_template_id", "annotation_template_version",
                "editor_template_id", "editor_template_version", "workspace_relative_path",
                "desired_state", "actual_state", "annotation_container_name",
                "annotation_container_state", "editor_container_name", "editor_container_state",
                "current_operation_id", "lock_version"}) {
            assertTrue("missing environment column " + column, sql.contains(column));
        }

        assertTrue(sql.contains("UNIQUE KEY uk_container_template_version (template_id, template_version)"));
        assertTrue(sql.contains("UNIQUE KEY uk_processing_environment_slot (agent_id, slot_number)"));
        assertTrue(sql.contains("UNIQUE KEY uk_processing_environment_user (agent_id, user_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_training_environment_assignment (user_id, course_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_environment_agent_port (agent_id, host_port, protocol)"));
        assertTrue(sql.contains("UNIQUE KEY uk_environment_active_operation (active_operation_key)"));
        assertTrue(sql.contains("command_id VARCHAR(36) NULL"));
        assertTrue(sql.contains("KEY idx_environment_agent_state (agent_id, actual_state)"));
        assertTrue(sql.contains("KEY idx_environment_operation_command (command_id)"));
        assertTrue(sql.contains("lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0"));
    }

    @Test
    public void applicationScansEnvironmentMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.environment.persistence"));
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
