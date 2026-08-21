package com.match.mode.persistence;

import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CompetitionModeSchemaTest {
    @Test
    public void migrationDefinesTwoLevelModeAndNullableBinding() throws Exception {
        String sql = read("/db/migration/V25__competition_mode_orchestration.sql");

        assertTrue(sql.contains("ALTER TABLE processing_environment_slot MODIFY user_id INT NULL"));
        assertTrue(sql.contains("CREATE TABLE platform_mode"));
        assertTrue(sql.contains("singleton_id TINYINT UNSIGNED NOT NULL"));
        assertTrue(sql.contains("CONSTRAINT chk_platform_mode_singleton CHECK (singleton_id = 1)"));
        assertTrue(sql.contains("generation BIGINT UNSIGNED NOT NULL"));
        assertTrue(sql.contains("VALUES (1, 'TRAINING', 1, 0, UTC_TIMESTAMP(3))"));

        assertTrue(sql.contains("CREATE TABLE competition_environment"));
        assertTrue(sql.contains("UNIQUE KEY uk_competition_environment_slot (slot_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_competition_annotation_name (annotation_container_name)"));
        assertTrue(sql.contains("UNIQUE KEY uk_competition_editor_name (editor_container_name)"));
        assertTrue(sql.contains("last_verified_at DATETIME(3) NULL"));
        assertTrue(sql.contains("last_component_results_json JSON NULL"));
        assertTrue(sql.contains("lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0"));

        assertTrue(sql.contains("CREATE TABLE processing_agent_mode"));
        assertTrue(sql.contains("CREATE TABLE mode_transition"));
        assertTrue(sql.contains("UNIQUE KEY uk_mode_active_transition (active_transition_key)"));
        assertTrue(sql.contains("CREATE TABLE mode_transition_step"));
        assertTrue(sql.contains("UNIQUE KEY uk_mode_transition_step "
                + "(transition_id, phase_number, step_ordinal)"));
        assertTrue(sql.contains("UNIQUE KEY uk_mode_step_idempotency (idempotency_key)"));
        assertTrue(sql.contains("component_results_json JSON NULL"));
        assertTrue(sql.contains("CREATE TABLE mode_training_snapshot"));
        assertTrue(sql.contains("PRIMARY KEY (transition_id, environment_id)"));
    }

    @Test
    public void slotRecordAllowsAnUnboundUser() throws Exception {
        assertEquals(Integer.class,
                ProcessingEnvironmentSlotRecord.class.getDeclaredField("userId").getType());
    }

    @Test
    public void slotMapperLocksAndMutatesOnlyTheExactAssignment() throws Exception {
        Method select = ProcessingEnvironmentSlotMapper.class.getMethod(
                "selectByAgentAndNumberForUpdate", String.class, int.class);
        String selectSql = sql(select.getAnnotation(Select.class).value());
        assertTrue(selectSql.contains("agent_id = #{agentId}"));
        assertTrue(selectSql.contains("slot_number = #{slotNumber}"));
        assertTrue(selectSql.endsWith("FOR UPDATE"));

        Method bind = ProcessingEnvironmentSlotMapper.class.getMethod(
                "bindUser", String.class, int.class, int.class, LocalDateTime.class);
        String bindSql = sql(bind.getAnnotation(Update.class).value());
        assertTrue(bindSql.contains("SET user_id = #{userId}, updated_at = #{updatedAt}"));
        assertTrue(bindSql.contains("agent_id = #{agentId}"));
        assertTrue(bindSql.contains("slot_number = #{slotNumber}"));
        assertTrue(bindSql.contains("user_id IS NULL"));

        Method unbind = ProcessingEnvironmentSlotMapper.class.getMethod(
                "unbindUser", String.class, int.class, int.class, LocalDateTime.class);
        String unbindSql = sql(unbind.getAnnotation(Update.class).value());
        assertTrue(unbindSql.contains("SET user_id = NULL, updated_at = #{updatedAt}"));
        assertTrue(unbindSql.contains("agent_id = #{agentId}"));
        assertTrue(unbindSql.contains("slot_number = #{slotNumber}"));
        assertTrue(unbindSql.contains("user_id = #{userId}"));
    }

    private String sql(String[] fragments) {
        assertNotNull(fragments);
        return String.join("", fragments).replaceAll("\\s+", " ").trim();
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
