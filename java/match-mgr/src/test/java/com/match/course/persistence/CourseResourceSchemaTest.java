package com.match.course.persistence;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class CourseResourceSchemaTest {
    @Test
    public void migrationDefinesCourseResourceProgressAndDeliveryTables() throws Exception {
        String sql = read("/db/migration/V28__course_resource_platform.sql");
        assertTrue(sql.contains("CREATE TABLE course"));
        assertTrue(sql.contains("CREATE TABLE course_resource"));
        assertTrue(sql.contains("CREATE TABLE course_resource_progress"));
        assertTrue(sql.contains("CREATE TABLE course_resource_delivery"));
        assertTrue(sql.contains("EBOOK"));
        assertTrue(sql.contains("VIDEO"));
        assertTrue(sql.contains("PPT"));
        assertTrue(sql.contains("ARCHIVE"));
        assertTrue(sql.contains("UNIQUE KEY uk_course_progress_user_resource"));
        assertTrue(sql.contains("UNIQUE KEY uk_course_delivery_idempotency"));
        assertTrue(sql.contains("sha256 CHAR(64)"));
        assertTrue(!sql.contains("FOREIGN KEY"));
    }

    @Test
    public void mapperContractsExposeLockingAndProgressOperations() throws Exception {
        assertTrue(CourseMapper.class.getMethod("selectForUpdate", String.class) != null);
        assertTrue(CourseResourceMapper.class.getMethod("selectVisible", Integer.class) != null);
        assertTrue(CourseProgressMapper.class.getMethod("upsertProgress", CourseProgressRecord.class) != null);
        assertTrue(CourseDeliveryMapper.class.getMethod("selectDeliveryForUpdate", String.class, Integer.class, String.class) != null);
    }

    private String read(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("missing resource " + path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
