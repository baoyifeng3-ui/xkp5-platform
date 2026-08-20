package com.match.licensing.service;

import com.match.licensing.persistence.LicenseAuditMapper;
import com.match.licensing.persistence.LicenseAuditRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LicenseAuditService {
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");

    private final LicenseAuditMapper mapper;
    private final Clock clock;

    public LicenseAuditService(LicenseAuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void recordSuccess(String action, Integer actorUserId, String requestId, String licenseId) {
        insert(action, "SUCCESS", null, actorUserId, requestId, licenseId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String reasonCode, Integer actorUserId,
                              String requestId, String licenseId) {
        String safeReason = reasonCode != null && SAFE_CODE.matcher(reasonCode).matches()
                ? reasonCode : "UNKNOWN";
        insert(action, "FAILURE", safeReason, actorUserId, requestId, licenseId);
    }

    private void insert(String action, String result, String reasonCode, Integer actorUserId,
                        String requestId, String licenseId) {
        if (action == null || !SAFE_CODE.matcher(action).matches()) {
            throw new IllegalArgumentException("审计操作代码无效");
        }
        LicenseAuditRecord record = new LicenseAuditRecord();
        record.setActorUserId(actorUserId);
        record.setAction(action);
        record.setResult(result);
        record.setReasonCode(reasonCode);
        record.setRequestId(requestId);
        record.setLicenseId(licenseId);
        record.setCorrelationId(UUID.randomUUID().toString());
        record.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        mapper.insert(record);
    }
}
