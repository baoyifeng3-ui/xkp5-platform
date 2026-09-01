package com.match.resource.persistence;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class ResourceSpaceSchemaTest {
    @Test
    public void migrationCreatesFourSpacesAndPreservesCourseResourceIds() throws Exception {
        String sql = read("/db/migration/V30__resource_spaces.sql");
        assertTrue(sql.contains("create table resource_directory"));
        assertTrue(sql.contains("create table platform_file"));
        assertTrue(sql.contains("create table course_resource_link"));
        assertTrue(sql.contains("create table resource_cleanup_audit"));
        for (String space : new String[]{"course", "public", "exchange", "homework"}) {
            assertTrue(sql.contains("'" + space + "'"));
        }
        assertTrue(sql.contains("insert into platform_file"));
        assertTrue(sql.contains("select resource_id"));
        assertTrue(sql.contains("insert into course_resource_link"));
    }

    private String read(String resource) throws Exception {
        InputStream input = getClass().getResourceAsStream(resource);
        assertTrue("missing migration " + resource, input != null);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8).toLowerCase();
        }
    }
}
