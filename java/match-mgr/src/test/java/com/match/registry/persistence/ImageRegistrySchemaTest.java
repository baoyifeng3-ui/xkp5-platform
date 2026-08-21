package com.match.registry.persistence;

import com.match.registry.model.ImageComponentType;
import com.match.registry.model.ImageGroupType;
import org.apache.ibatis.annotations.Select;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ImageRegistrySchemaTest {
    @Test
    public void definesStableCatalogTypes() {
        assertEquals(ImageGroupType.STANDARD_PAIR, ImageGroupType.valueOf("STANDARD_PAIR"));
        assertEquals(ImageGroupType.CUSTOM, ImageGroupType.valueOf("CUSTOM"));
        assertEquals(ImageComponentType.ANNOTATION, ImageComponentType.valueOf("ANNOTATION"));
        assertEquals(ImageComponentType.EDITOR, ImageComponentType.valueOf("EDITOR"));
    }

    @Test
    public void migrationDefinesCatalogAndImmutableDigests() throws Exception {
        String sql = read("/db/migration/V26__image_registry_catalog.sql");
        assertTrue(sql.contains("CREATE TABLE image_group"));
        assertTrue(sql.contains("group_type VARCHAR(24) NOT NULL"));
        assertTrue(sql.contains("CREATE TABLE image_artifact"));
        assertTrue(sql.contains("sha256 CHAR(64) NOT NULL"));
        assertTrue(sql.contains("UNIQUE KEY uk_image_artifact_sha256 (sha256)"));
        assertTrue(sql.contains("registry_digest CHAR(71)"));
        assertTrue(sql.contains("CREATE TABLE image_upload"));
        assertTrue(sql.contains("CREATE TABLE image_upload_chunk"));
        assertTrue(sql.contains("UNIQUE KEY uk_image_upload_chunk (upload_id, chunk_index)"));
        assertTrue(sql.contains("CREATE TABLE image_release"));
        assertTrue(sql.contains("CREATE TABLE image_deployment"));
        assertTrue(sql.contains("UNIQUE KEY uk_image_deployment_active (active_deployment_key)"));
        assertTrue(sql.contains("failure_code VARCHAR(64) NULL"));
        assertTrue(sql.contains("failure_message VARCHAR(512) NULL"));
        assertTrue(sql.contains("CHECK (size_bytes >= 0)"));
        assertTrue(sql.contains("CHECK (chunk_index >= 0)"));
        assertTrue(sql.contains("CHECK (received_bytes >= 0)"));
        assertTrue(!sql.contains("FOREIGN KEY"));
    }

    @Test
    public void mappersExposeLockingBoundaries() throws Exception {
        Method upload = ImageUploadMapper.class.getMethod("selectForUpdate", String.class);
        assertTrue(normalize(upload.getAnnotation(Select.class).value()).endsWith("FOR UPDATE"));
        Method review = ImageArtifactMapper.class.getMethod("selectPendingReviewForUpdate", String.class);
        assertTrue(normalize(review.getAnnotation(Select.class).value()).endsWith("FOR UPDATE"));
        Method deployment = ImageDeploymentMapper.class.getMethod(
                "selectActiveForUpdate", String.class, String.class);
        assertTrue(normalize(deployment.getAnnotation(Select.class).value()).endsWith("FOR UPDATE"));
    }

    @Test
    public void recordsContainImmutableAndFailureMetadata() throws Exception {
        assertEquals(String.class, ImageArtifactRecord.class.getDeclaredField("sha256").getType());
        assertEquals(String.class, ImageArtifactRecord.class.getDeclaredField("registryDigest").getType());
        assertEquals(Long.class, ImageUploadRecord.class.getDeclaredField("totalSize").getType());
        assertEquals(Long.class, ImageUploadRecord.class.getDeclaredField("receivedBytes").getType());
        assertEquals(String.class, ImageArtifactRecord.class.getDeclaredField("failureCode").getType());
        assertEquals(String.class, ImageDeploymentRecord.class.getDeclaredField("failureMessage").getType());
    }

    private String normalize(String[] sql) {
        assertNotNull(sql);
        return String.join("", sql).replaceAll("\\s+", " ").trim();
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
