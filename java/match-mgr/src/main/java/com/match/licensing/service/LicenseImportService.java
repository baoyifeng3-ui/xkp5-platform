package com.match.licensing.service;

import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.model.LicensePayload;
import com.match.licensing.model.LicenseState;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import com.match.licensing.persistence.PlatformLicenseMapper;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LicenseImportService {
    private final LicenseValidationService validationService;
    private final PlatformLicenseMapper licenseMapper;
    private final LicenseRequestMapper requestMapper;
    private final LicenseAuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LicenseImportService(LicenseValidationService validationService, PlatformLicenseMapper licenseMapper,
                                LicenseRequestMapper requestMapper, LicenseAuditService auditService,
                                ApplicationEventPublisher events, Clock clock) {
        this.validationService = validationService;
        this.licenseMapper = licenseMapper;
        this.requestMapper = requestMapper;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PlatformLicenseRecord importLicense(byte[] fileBytes, int actorUserId) {
        final LicenseValidationService.ValidatedLicense validated;
        try {
            validated = validationService.validate(fileBytes);
        } catch (InvalidLicenseException exception) {
            auditService.recordFailure("LICENSE_IMPORT", exception.getReasonCode(), actorUserId, null, null);
            throw exception;
        }

        LicensePayload payload = validated.getPayload();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        PlatformLicenseRecord record = new PlatformLicenseRecord();
        record.setLicenseId(payload.getLicenseId());
        record.setActive(true);
        record.setRequestId(payload.getRequestId());
        record.setOrganization(payload.getOrganization());
        record.setEnvironment(payload.getEnvironment());
        record.setFingerprintDigest(payload.getFingerprint());
        record.setKeyId(payload.getKeyId());
        record.setNotBefore(LocalDateTime.ofInstant(payload.getNotBefore(), ZoneOffset.UTC));
        record.setExpiresAt(LocalDateTime.ofInstant(payload.getExpiresAt(), ZoneOffset.UTC));
        record.setMaxProcessingServers(payload.getMaxProcessingServers());
        record.setSignedEnvelope(validated.getSignedEnvelope());
        record.setImportedBy(actorUserId);
        record.setImportedAt(now);

        licenseMapper.deactivateAll();
        licenseMapper.insert(record);
        LicenseRequestRecord request = validated.getRequest();
        request.setConsumedAt(now);
        request.setConsumedLicenseId(payload.getLicenseId());
        requestMapper.updateById(request);
        auditService.recordSuccess("LICENSE_IMPORTED", actorUserId, payload.getRequestId(), payload.getLicenseId());
        events.publishEvent(new LicenseStateChangedEvent(null, LicenseState.ACTIVE, payload.getLicenseId()));
        return record;
    }
}
