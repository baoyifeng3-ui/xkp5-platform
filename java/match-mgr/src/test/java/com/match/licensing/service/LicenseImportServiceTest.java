package com.match.licensing.service;

import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.model.LicensePayload;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import com.match.licensing.persistence.PlatformLicenseMapper;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseImportServiceTest {
    private LicenseValidationService validationService;
    private PlatformLicenseMapper licenseMapper;
    private LicenseRequestMapper requestMapper;
    private LicenseAuditService auditService;
    private ApplicationEventPublisher events;
    private LicenseImportService service;

    @Before
    public void setUp() {
        validationService = mock(LicenseValidationService.class);
        licenseMapper = mock(PlatformLicenseMapper.class);
        requestMapper = mock(LicenseRequestMapper.class);
        auditService = mock(LicenseAuditService.class);
        events = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        service = new LicenseImportService(validationService, licenseMapper, requestMapper,
                auditService, events, clock);
    }

    @Test
    public void invalidReplacementKeepsCurrentLicenseActive() {
        byte[] tampered = "tampered".getBytes();
        when(validationService.validate(tampered))
                .thenThrow(new InvalidLicenseException("INVALID_SIGNATURE", "bad signature"));

        try {
            service.importLicense(tampered, 7);
            fail("tampered license must be rejected");
        } catch (InvalidLicenseException expected) {
            assertEquals("INVALID_SIGNATURE", expected.getReasonCode());
        }

        verify(licenseMapper, never()).deactivateAll();
        verify(licenseMapper, never()).insert(any(PlatformLicenseRecord.class));
        verify(auditService).recordFailure("LICENSE_IMPORT", "INVALID_SIGNATURE", 7, null, null);
    }

    @Test
    public void successfulImportConsumesRequestAndPublishesActiveEvent() {
        LicensePayload payload = payload();
        LicenseRequestRecord request = new LicenseRequestRecord();
        request.setRequestId(payload.getRequestId());
        when(validationService.validate(any(byte[].class)))
                .thenReturn(new LicenseValidationService.ValidatedLicense(payload, "signed-envelope", request));

        PlatformLicenseRecord result = service.importLicense("valid".getBytes(), 7);

        assertTrue(result.getActive());
        verify(licenseMapper).deactivateAll();
        verify(licenseMapper).insert(result);
        verify(requestMapper).updateById(request);
        assertTrue(request.getConsumedAt() != null);
        assertEquals(payload.getLicenseId(), request.getConsumedLicenseId());
        verify(events).publishEvent(any(LicenseStateChangedEvent.class));
        verify(auditService).recordSuccess("LICENSE_IMPORTED", 7, payload.getRequestId(), payload.getLicenseId());
    }

    private LicensePayload payload() {
        return new LicensePayload(1, "license-1", "key-1", "XKP5", 5,
                "request-1", "installation-1", "challenge", "sha256:fingerprint",
                "DEVELOPMENT", "Fixture Lab", Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2027-08-18T00:00:00Z"), 4,
                Instant.parse("2026-08-18T00:00:00Z"));
    }
}
