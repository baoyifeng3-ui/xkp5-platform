package com.match.dashboard.persistence;

import com.match.Application;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Select;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UserSessionActivitySchemaTest {
    @Test
    public void migrationDefinesDigestKeyedSessionActivity() throws Exception {
        String sql = read("/db/migration/V23__user_session_activity.sql");

        assertTrue(sql.contains("CREATE TABLE user_session_activity"));
        assertTrue(sql.contains("session_digest CHAR(64) NOT NULL"));
        assertTrue(sql.contains("user_id INT NOT NULL"));
        assertTrue(sql.contains("login_at DATETIME(3) NOT NULL"));
        assertTrue(sql.contains("last_activity_at DATETIME(3) NOT NULL"));
        assertTrue(sql.contains("expires_at DATETIME(3) NOT NULL"));
        assertTrue(sql.contains("UNIQUE KEY uk_user_session_activity_digest (session_digest)"));
        assertTrue(sql.contains("KEY idx_user_session_activity_expiry (expires_at)"));
        assertTrue(sql.contains("KEY idx_user_session_activity_user_expiry (user_id, expires_at)"));
    }

    @Test
    public void applicationScansDashboardMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.dashboard.persistence"));
    }

    @Test
    public void onlineCountRequiresSessionExpiryAfterCurrentTime() throws Exception {
        Select select = UserSessionActivityMapper.class
                .getMethod("countDistinctActiveUsers", java.time.LocalDateTime.class)
                .getAnnotation(Select.class);

        assertNotNull(select);
        assertTrue(String.join(" ", select.value()).contains(
                "expires_at > DATE_ADD(#{cutoff}, INTERVAL 5 MINUTE)"));
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
