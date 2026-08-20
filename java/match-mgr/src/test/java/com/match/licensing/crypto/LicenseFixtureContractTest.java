package com.match.licensing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class LicenseFixtureContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void requestFixtureContainsOnlyVersionedPublicPlatformData() throws Exception {
        JsonNode request = read("/licensing/request-v1.xkpreq");

        assertEquals(1, request.get("formatVersion").asInt());
        assertEquals("DEVELOPMENT", request.get("environment").asText());
        assertEquals("5.0.0", request.get("platformVersion").asText());
        assertFalse(request.has("password"));
        assertFalse(request.has("privateKey"));
        assertFalse(request.has("machineId"));
    }

    @Test
    public void licenseFixtureUsesVersionedXkp5Envelope() throws Exception {
        JsonNode envelope = read("/licensing/license-v1.xkplic");

        assertEquals(1, envelope.get("formatVersion").asInt());
        assertEquals("test-2026-01", envelope.get("keyId").asText());
        assertEquals("XKP5", envelope.get("payload").get("product").asText());
        assertEquals("DEVELOPMENT", envelope.get("payload").get("environment").asText());
        assertFalse(envelope.has("privateKey"));
    }

    @Test
    public void goToolFixtureHasAValidEd25519Signature() throws Exception {
        String license = readText("/licensing/license-v1.xkplic");
        String publicKey = readText("/licensing/public-v1.key").trim();

        JsonNode payload = new LicenseSignatureVerifier(new StrictJson(), new CanonicalJson())
                .verify(license, Collections.singletonMap("test-2026-01", publicKey));

        assertEquals("00000000-0000-0000-0000-000000000002", payload.get("requestId").asText());
        assertEquals(4, payload.get("maxProcessingServers").asInt());
    }

    private JsonNode read(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("missing fixture " + path, input);
        try (InputStream closeable = input) {
            return objectMapper.readTree(closeable);
        }
    }

    private String readText(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("missing fixture " + path, input);
        try (InputStream closeable = input) {
            byte[] buffer = new byte[8192];
            StringBuilder content = new StringBuilder();
            int count;
            while ((count = closeable.read(buffer)) != -1) {
                content.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            }
            return content.toString();
        }
    }
}
