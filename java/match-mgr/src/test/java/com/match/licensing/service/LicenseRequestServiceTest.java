package com.match.licensing.service;

import com.match.licensing.crypto.CanonicalJson;
import com.match.licensing.crypto.Digests;
import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.model.PlatformRequest;
import com.match.licensing.persistence.LicenseAuditMapper;
import com.match.licensing.persistence.LicenseAuditRecord;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseRequestServiceTest {
    private LicenseRequestMapper requestMapper;
    private LicenseAuditService auditService;
    private LicenseRequestService service;

    @Before
    public void setUp() {
        InstallationService installationService = mock(InstallationService.class);
        when(installationService.installationId()).thenReturn("installation-1");
        HostIdentityProvider identityProvider = () ->
                new HostIdentity("sha256:host-fingerprint", "DEVELOPMENT");
        requestMapper = mock(LicenseRequestMapper.class);
        auditService = mock(LicenseAuditService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        service = new LicenseRequestService(installationService, identityProvider, requestMapper,
                auditService, new CanonicalJson(), new SecureRandom(), clock, "5.0.0");
    }

    @Test
    public void createsFreshChallengeWithoutExportingRawHardware() {
        PlatformRequest first = service.create(" Fixture Lab ", 7);
        PlatformRequest second = service.create("Fixture Lab", 7);

        assertNotEquals(first.getRequestId(), second.getRequestId());
        assertNotEquals(first.getChallenge(), second.getChallenge());
        assertTrue(first.getFingerprint().startsWith("sha256:"));
        String json = new String(service.write(first), StandardCharsets.UTF_8);
        assertFalse(json.contains("machineId"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("privateKey"));
    }

    @Test
    public void storesOnlyChallengeHashAndAuditsSuccess() {
        PlatformRequest request = service.create("Fixture Lab", 7);

        ArgumentCaptor<LicenseRequestRecord> record = ArgumentCaptor.forClass(LicenseRequestRecord.class);
        verify(requestMapper).insert(record.capture());
        assertEquals(Digests.sha256(request.getChallenge()), record.getValue().getChallengeHash());
        assertFalse(record.getValue().getChallengeHash().contains(request.getChallenge()));
        assertEquals("sha256:host-fingerprint", record.getValue().getFingerprintDigest());
        verify(auditService).recordSuccess("REQUEST_CREATED", 7, request.getRequestId(), null);
    }

    @Test
    public void writesCanonicalUtf8RequestAndFilename() {
        PlatformRequest request = service.create(null, 7);
        String json = new String(service.write(request), StandardCharsets.UTF_8);

        assertTrue(json.startsWith("{\"challenge\":"));
        assertTrue(json.contains("\"createdAt\":\"2026-08-18T00:00:00Z\""));
        assertTrue(json.contains("\"formatVersion\":1"));
        assertEquals("xkp-platform-" + request.getRequestId() + ".xkpreq", service.filename(request));
        assertEquals("application/json;charset=UTF-8", LicenseRequestService.CONTENT_TYPE);
    }

    @Test
    public void trimsOrganizationAndRejectsOverTwoHundredCharacters() {
        assertEquals("Fixture Lab", service.create(" Fixture Lab ", 7).getOrganization());
        try {
            service.create(repeat('x', 201), 7);
            fail("long organization must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("组织名称不能超过200个字符", expected.getMessage());
        }
    }

    @Test
    public void auditFailureUsesIndependentSafeRecord() {
        LicenseAuditMapper mapper = mock(LicenseAuditMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        LicenseAuditService audit = new LicenseAuditService(mapper, clock);

        audit.recordFailure("LICENSE_IMPORT", "bad message: secret=123", 7, "request-1", null);

        ArgumentCaptor<LicenseAuditRecord> record = ArgumentCaptor.forClass(LicenseAuditRecord.class);
        verify(mapper).insert(record.capture());
        assertEquals("FAILURE", record.getValue().getResult());
        assertEquals("UNKNOWN", record.getValue().getReasonCode());
        assertEquals("request-1", record.getValue().getRequestId());
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
