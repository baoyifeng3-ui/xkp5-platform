package com.match.licensing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.match.licensing.config.LicenseProperties;
import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.crypto.LicenseSignatureVerifier;
import com.match.licensing.crypto.StrictJson;
import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseValidationServiceTest {
    private LicenseSignatureVerifier verifier;
    private LicenseRequestMapper requestMapper;
    private LicenseValidationService service;
    private JsonNode payload;

    @Before
    public void setUp() {
        verifier = mock(LicenseSignatureVerifier.class);
        requestMapper = mock(LicenseRequestMapper.class);
        LicenseProperties properties = new LicenseProperties();
        properties.setTestPublicKey("test-key=AAAA");
        HostIdentityProvider identity = () -> new HostIdentity("sha256:fingerprint", "DEVELOPMENT");
        InstallationService installation = mock(InstallationService.class);
        when(installation.installationId()).thenReturn("installation-1");
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        service = new LicenseValidationService(verifier, properties, identity, installation, requestMapper, clock);
        payload = new StrictJson().readTree(payloadJson());
    }

    @Test
    public void validatesMatchingUnconsumedRequestUsingDevelopmentKeyOnly() {
        when(verifier.verify(any(String.class), eq(Collections.singletonMap("test-key", "AAAA"))))
                .thenReturn(payload);
        when(requestMapper.selectByIdForUpdate("request-1")).thenReturn(request());

        LicenseValidationService.ValidatedLicense result = service.validate("{}".getBytes(StandardCharsets.UTF_8));

        assertEquals("license-1", result.getPayload().getLicenseId());
        assertEquals("request-1", result.getRequest().getRequestId());
    }

    @Test
    public void rejectsOversizedFileBeforeSignatureParsing() {
        try {
            service.validate(new byte[256 * 1024 + 1]);
            fail("oversized file must be rejected");
        } catch (InvalidLicenseException expected) {
            assertEquals("INVALID_FILE_SIZE", expected.getReasonCode());
        }
        verify(verifier, never()).verify(any(String.class), any());
    }

    @Test
    public void storedLicenseMustMatchItsSignedEnvelope() {
        when(verifier.verify(eq("signed-envelope"), any())).thenReturn(payload);
        PlatformLicenseRecord record = storedLicense();
        service.validateStored(record);

        record.setMaxProcessingServers(5);
        try {
            service.validateStored(record);
            fail("modified database limit must be rejected");
        } catch (InvalidLicenseException expected) {
            assertEquals("STORED_LICENSE_MISMATCH", expected.getReasonCode());
        }
    }

    private LicenseRequestRecord request() {
        LicenseRequestRecord request = new LicenseRequestRecord();
        request.setRequestId("request-1");
        request.setInstallationId("installation-1");
        request.setChallengeHash(com.match.licensing.crypto.Digests.sha256("challenge"));
        request.setFingerprintDigest("sha256:fingerprint");
        request.setEnvironment("DEVELOPMENT");
        return request;
    }

    private PlatformLicenseRecord storedLicense() {
        PlatformLicenseRecord record = new PlatformLicenseRecord();
        record.setLicenseId("license-1");
        record.setRequestId("request-1");
        record.setOrganization("Fixture Lab");
        record.setEnvironment("DEVELOPMENT");
        record.setFingerprintDigest("sha256:fingerprint");
        record.setKeyId("test-key");
        record.setNotBefore(LocalDateTime.ofInstant(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC));
        record.setExpiresAt(LocalDateTime.ofInstant(Instant.parse("2027-08-18T00:00:00Z"), ZoneOffset.UTC));
        record.setMaxProcessingServers(4);
        record.setSignedEnvelope("signed-envelope");
        return record;
    }

    private String payloadJson() {
        return "{\"version\":1,\"licenseId\":\"license-1\",\"keyId\":\"test-key\","
                + "\"product\":\"XKP5\",\"supportedMajorVersion\":5,\"requestId\":\"request-1\","
                + "\"installationId\":\"installation-1\",\"challenge\":\"challenge\","
                + "\"fingerprint\":\"sha256:fingerprint\",\"environment\":\"DEVELOPMENT\","
                + "\"organization\":\"Fixture Lab\",\"notBefore\":\"2026-08-18T00:00:00Z\","
                + "\"expiresAt\":\"2027-08-18T00:00:00Z\",\"maxProcessingServers\":4,"
                + "\"issuedAt\":\"2026-08-18T00:00:00Z\"}";
    }
}
