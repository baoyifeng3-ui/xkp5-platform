package com.match.licensing.service;

import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.persistence.PlatformInstallation;
import com.match.licensing.persistence.PlatformLicenseMapper;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseStatusServiceTest {
    private PlatformLicenseMapper licenseMapper;
    private InstallationService installationService;
    private Clock clock;

    @Before
    public void setUp() {
        licenseMapper = mock(PlatformLicenseMapper.class);
        installationService = mock(InstallationService.class);
        clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        PlatformInstallation installation = new PlatformInstallation();
        installation.setInstallationKey("PRIMARY");
        installation.setInstallationId("installation-1");
        when(installationService.installation()).thenReturn(installation);
    }

    @Test
    public void reportsNotActivatedWithoutLicense() {
        assertEquals(LicenseState.NOT_ACTIVATED, service().currentStatus().getState());
    }

    @Test
    public void reportsActiveAndExpiringAtThirtyDays() {
        PlatformLicenseRecord active = licenseAt("2026-10-01T00:00:00Z");
        when(licenseMapper.selectActive()).thenReturn(active);
        assertEquals(LicenseState.ACTIVE, service().currentStatus().getState());

        when(licenseMapper.selectActive()).thenReturn(licenseAt("2026-09-17T00:00:00Z"));
        LicenseStatus expiring = service().currentStatus();
        assertEquals(LicenseState.EXPIRING, expiring.getState());
        assertTrue(expiring.isUsable());
    }

    @Test
    public void reportsExpiredAndInvalidHostBinding() {
        when(licenseMapper.selectActive()).thenReturn(licenseAt("2026-08-18T00:00:00Z"));
        assertEquals(LicenseState.EXPIRED, service().currentStatus().getState());

        PlatformLicenseRecord wrongHost = licenseAt("2027-08-18T00:00:00Z");
        wrongHost.setFingerprintDigest("sha256:other");
        when(licenseMapper.selectActive()).thenReturn(wrongHost);
        assertEquals(LicenseState.INVALID, service().currentStatus().getState());
    }

    @Test
    public void detectsClockRollbackBeforeUpdatingTrustedTime() {
        PlatformInstallation installation = new PlatformInstallation();
        installation.setInstallationKey("PRIMARY");
        installation.setInstallationId("installation-1");
        installation.setMaxTrustedTime(LocalDateTime.ofInstant(
                Instant.parse("2026-08-18T00:06:00Z"), ZoneOffset.UTC));
        when(installationService.installation()).thenReturn(installation);
        when(licenseMapper.selectActive()).thenReturn(licenseAt("2027-08-18T00:00:00Z"));

        LicenseStatus status = service().currentStatus();

        assertEquals(LicenseState.CLOCK_ROLLBACK, status.getState());
        assertFalse(status.isUsable());
    }

    @Test
    public void movesTrustedTimeForward() {
        when(licenseMapper.selectActive()).thenReturn(licenseAt("2027-08-18T00:00:00Z"));

        service().currentStatus();

        verify(installationService).updateMaxTrustedTime(any(LocalDateTime.class));
    }

    private LicenseStatusService service() {
        HostIdentityProvider identity = () -> new HostIdentity("sha256:fingerprint", "DEVELOPMENT");
        LicenseValidationService validationService = mock(LicenseValidationService.class);
        return new LicenseStatusService(licenseMapper, installationService, identity, validationService, clock);
    }

    private PlatformLicenseRecord licenseAt(String expiresAt) {
        PlatformLicenseRecord record = new PlatformLicenseRecord();
        record.setLicenseId("license-1");
        record.setActive(true);
        record.setOrganization("Fixture Lab");
        record.setEnvironment("DEVELOPMENT");
        record.setFingerprintDigest("sha256:fingerprint");
        record.setNotBefore(LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        record.setExpiresAt(LocalDateTime.ofInstant(Instant.parse(expiresAt), ZoneOffset.UTC));
        record.setMaxProcessingServers(4);
        return record;
    }
}
