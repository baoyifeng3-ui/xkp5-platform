package com.match.licensing.service;

import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.persistence.PlatformInstallation;
import com.match.licensing.persistence.PlatformLicenseMapper;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LicenseStatusService {
    private static final Duration CLOCK_ROLLBACK_TOLERANCE = Duration.ofMinutes(5);
    private static final Duration EXPIRING_WINDOW = Duration.ofDays(30);
    private final PlatformLicenseMapper licenseMapper;
    private final InstallationService installationService;
    private final HostIdentityProvider identityProvider;
    private final LicenseValidationService validationService;
    private final Clock clock;

    public LicenseStatusService(PlatformLicenseMapper licenseMapper, InstallationService installationService,
                                HostIdentityProvider identityProvider,
                                LicenseValidationService validationService, Clock clock) {
        this.licenseMapper = licenseMapper;
        this.installationService = installationService;
        this.identityProvider = identityProvider;
        this.validationService = validationService;
        this.clock = clock;
    }

    public LicenseStatus currentStatus() {
        Instant now = clock.instant();
        PlatformInstallation installation = installationService.installation();
        if (isClockRollback(installation, now)) {
            return status(LicenseState.CLOCK_ROLLBACK, licenseMapper.selectActive());
        }
        PlatformLicenseRecord license = licenseMapper.selectActive();
        if (license == null) {
            return status(LicenseState.NOT_ACTIVATED, null);
        }
        HostIdentity identity = identityProvider.load();
        if (!Boolean.TRUE.equals(license.getActive())
                || !identity.getEnvironment().equals(license.getEnvironment())
                || !identity.getFingerprint().equals(license.getFingerprintDigest())
                || license.getNotBefore() == null || license.getExpiresAt() == null) {
            return status(LicenseState.INVALID, license);
        }
        try {
            validationService.validateStored(license);
        } catch (InvalidLicenseException exception) {
            return status(LicenseState.INVALID, license);
        }
        Instant notBefore = license.getNotBefore().toInstant(ZoneOffset.UTC);
        Instant expiresAt = license.getExpiresAt().toInstant(ZoneOffset.UTC);
        if (now.isBefore(notBefore)) {
            return status(LicenseState.INVALID, license);
        }
        if (!now.isBefore(expiresAt)) {
            return status(LicenseState.EXPIRED, license);
        }
        installationService.updateMaxTrustedTime(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (!expiresAt.isAfter(now.plus(EXPIRING_WINDOW))) {
            return status(LicenseState.EXPIRING, license);
        }
        return status(LicenseState.ACTIVE, license);
    }

    private boolean isClockRollback(PlatformInstallation installation, Instant now) {
        if (installation.getMaxTrustedTime() == null) {
            return false;
        }
        Instant trusted = installation.getMaxTrustedTime().toInstant(ZoneOffset.UTC);
        return now.isBefore(trusted.minus(CLOCK_ROLLBACK_TOLERANCE));
    }

    private LicenseStatus status(LicenseState state, PlatformLicenseRecord license) {
        return new LicenseStatus(state,
                license == null ? null : license.getLicenseId(),
                license == null ? null : license.getOrganization(),
                license == null || license.getExpiresAt() == null
                        ? null : license.getExpiresAt().toInstant(ZoneOffset.UTC),
                license == null ? null : license.getMaxProcessingServers());
    }
}
