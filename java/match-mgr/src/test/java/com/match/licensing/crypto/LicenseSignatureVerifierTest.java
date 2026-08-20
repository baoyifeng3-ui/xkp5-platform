package com.match.licensing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.licensing.config.LicenseProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LicenseSignatureVerifierTest {
    private static final String KEY_ID = "test-2026-01";

    private StrictJson strictJson;
    private CanonicalJson canonicalJson;
    private LicenseSignatureVerifier verifier;
    private KeyPair keyPair;
    private Map<String, String> publicKeys;

    @Before
    public void setUp() throws Exception {
        strictJson = new StrictJson();
        canonicalJson = new CanonicalJson();
        verifier = new LicenseSignatureVerifier(strictJson, canonicalJson);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", new BouncyCastleProvider());
        keyPair = generator.generateKeyPair();
        publicKeys = Collections.singletonMap(KEY_ID,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    @Test
    public void acceptsValidSignatureAndReturnsPayload() throws Exception {
        String envelope = signedEnvelope(payload(KEY_ID), keyPair, KEY_ID);

        JsonNode verified = verifier.verify(envelope, publicKeys);

        assertEquals("license-1", verified.path("licenseId").asText());
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsChangedPayload() throws Exception {
        String envelope = signedEnvelope(payload(KEY_ID), keyPair, KEY_ID)
                .replace("Fixture Lab", "Changed Lab");

        verifier.verify(envelope, publicKeys);
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsUnknownKeyId() throws Exception {
        verifier.verify(signedEnvelope(payload(KEY_ID), keyPair, KEY_ID), Collections.<String, String>emptyMap());
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsEnvelopeAndPayloadKeyIdMismatch() throws Exception {
        verifier.verify(signedEnvelope(payload("other-key"), keyPair, KEY_ID), publicKeys);
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsSignatureFromAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", new BouncyCastleProvider());
        KeyPair otherKey = generator.generateKeyPair();

        verifier.verify(signedEnvelope(payload(KEY_ID), otherKey, KEY_ID), publicKeys);
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsUnexpectedEnvelopeFields() throws Exception {
        String envelope = signedEnvelope(payload(KEY_ID), keyPair, KEY_ID)
                .replace("\"signature\":", "\"unexpected\":true,\"signature\":");

        verifier.verify(envelope, publicKeys);
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsStringFormatVersion() throws Exception {
        String envelope = signedEnvelope(payload(KEY_ID), keyPair, KEY_ID)
                .replace("\"formatVersion\":1", "\"formatVersion\":\"1\"");

        verifier.verify(envelope, publicKeys);
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsDuplicateConfiguredKeyIds() {
        LicenseProperties properties = new LicenseProperties();
        properties.setPublicKeys("main=AAAA,main=BBBB");

        properties.productionKeys();
    }

    @Test(expected = InvalidLicenseException.class)
    public void rejectsMultipleDevelopmentKeys() {
        LicenseProperties properties = new LicenseProperties();
        properties.setTestPublicKey("test-a=AAAA,test-b=BBBB");

        properties.developmentKeys();
    }

    private JsonNode payload(String keyId) {
        return strictJson.readTree("{\"version\":1,\"licenseId\":\"license-1\",\"keyId\":\"" + keyId
                + "\",\"organization\":\"Fixture Lab\"}");
    }

    private String signedEnvelope(JsonNode payload, KeyPair signingKey, String keyId) throws Exception {
        Signature signer = Signature.getInstance("Ed25519", new BouncyCastleProvider());
        signer.initSign(signingKey.getPrivate());
        signer.update(canonicalJson.writeBytes(payload));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("formatVersion", 1);
        envelope.put("keyId", keyId);
        envelope.put("payload", payload);
        envelope.put("signature", signature);
        return new String(new ObjectMapper().writeValueAsBytes(envelope), StandardCharsets.UTF_8);
    }
}
