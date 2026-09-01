package com.match.registry.persistence;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class ImageImportProgressSchemaTest {
    @Test
    public void migrationPersistsObservableImportProgress() throws Exception {
        String sql = read("/db/migration/V29__image_import_progress.sql");

        assertTrue(sql.contains("import_stage"));
        assertTrue(sql.contains("import_progress"));
        assertTrue(sql.contains("import_completed_layers"));
        assertTrue(sql.contains("import_total_layers"));
        assertTrue(sql.contains("import_updated_at"));
    }

    private String read(String resource) throws Exception {
        InputStream input = getClass().getResourceAsStream(resource);
        assertTrue("missing migration " + resource, input != null);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).toLowerCase();
        }
    }
}
