package com.match.licensing.persistence;

import com.match.Application;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LicensingSchemaTest {
    @Test
    public void migrationCreatesAllLicensingTablesAndSingleActiveConstraint() throws Exception {
        String sql = read("/db/migration/V17__platform_licensing.sql");

        assertTrue(sql.contains("CREATE TABLE platform_installation"));
        assertTrue(sql.contains("CREATE TABLE license_request"));
        assertTrue(sql.contains("CREATE TABLE platform_license"));
        assertTrue(sql.contains("CREATE TABLE license_audit"));
        assertTrue(sql.contains("active_slot"));
        assertTrue(sql.contains("uk_platform_license_active"));
    }

    @Test
    public void applicationScansLicensingMappers() {
        MapperScan mapperScan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.mapper"));
        assertTrue(Arrays.asList(mapperScan.value()).contains("com.match.licensing.persistence"));
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
