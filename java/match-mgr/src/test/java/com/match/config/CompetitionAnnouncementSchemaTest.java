package com.match.config;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompetitionAnnouncementSchemaTest {
    @Test
    public void announcementEntriesAreIndependentFromAccounts() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/db/migration/V52__competition_announcement_entries.sql")),
                StandardCharsets.UTF_8).toLowerCase();
        assertTrue(sql.contains("create table competition_announcement_entry"));
        assertTrue(sql.contains("values_json json"));
        assertTrue(sql.contains("sort_order int"));
        assertFalse(sql.contains("user_id"));
    }
}
