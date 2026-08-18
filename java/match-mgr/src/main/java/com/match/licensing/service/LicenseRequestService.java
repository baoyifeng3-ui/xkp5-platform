package com.match.licensing.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.match.licensing.crypto.CanonicalJson;
import com.match.licensing.crypto.Digests;
import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.model.PlatformRequest;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
public class LicenseRequestService {
    public static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    private final InstallationService installationService;
    private final HostIdentityProvider identityProvider;
    private final LicenseRequestMapper requestMapper;
    private final LicenseAuditService auditService;
    private final CanonicalJson canonicalJson;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final String platformVersion;

    public LicenseRequestService(InstallationService installationService,
                                 HostIdentityProvider identityProvider,
                                 LicenseRequestMapper requestMapper,
                                 LicenseAuditService auditService,
                                 CanonicalJson canonicalJson,
                                 SecureRandom secureRandom,
                                 Clock clock,
                                 @Value("${xkp.platform-version:5.0.0}") String platformVersion) {
        this.installationService = installationService;
        this.identityProvider = identityProvider;
        this.requestMapper = requestMapper;
        this.auditService = auditService;
        this.canonicalJson = canonicalJson;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.platformVersion = platformVersion;
    }

    @Transactional
    public PlatformRequest create(String organization, int actorUserId) {
        String normalizedOrganization = normalizeOrganization(organization);
        HostIdentity identity = identityProvider.load();
        String installationId = installationService.installationId();
        String requestId = UUID.randomUUID().toString();
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        Instant now = clock.instant();
        LocalDateTime createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        LicenseRequestRecord record = new LicenseRequestRecord();
        record.setRequestId(requestId);
        record.setInstallationId(installationId);
        record.setChallengeHash(Digests.sha256(challenge));
        record.setFingerprintDigest(identity.getFingerprint());
        record.setEnvironment(identity.getEnvironment());
        record.setOrganization(normalizedOrganization);
        record.setCreatedBy(actorUserId);
        record.setCreatedAt(createdAt);
        requestMapper.insert(record);
        auditService.recordSuccess("REQUEST_CREATED", actorUserId, requestId, null);

        return new PlatformRequest(1, requestId, installationId, challenge, identity.getFingerprint(),
                identity.getEnvironment(), normalizedOrganization, platformVersion, now.toString());
    }

    public byte[] write(PlatformRequest request) {
        ObjectNode value = new ObjectNode(JsonNodeFactory.instance);
        value.put("formatVersion", request.getFormatVersion());
        value.put("requestId", request.getRequestId());
        value.put("installationId", request.getInstallationId());
        value.put("challenge", request.getChallenge());
        value.put("fingerprint", request.getFingerprint());
        value.put("environment", request.getEnvironment());
        if (request.getOrganization() != null) {
            value.put("organization", request.getOrganization());
        }
        value.put("platformVersion", request.getPlatformVersion());
        value.put("createdAt", request.getCreatedAt());
        return canonicalJson.writeBytes(value);
    }

    public String filename(PlatformRequest request) {
        return "xkp-platform-" + request.getRequestId() + ".xkpreq";
    }

    private String normalizeOrganization(String organization) {
        if (organization == null || organization.trim().isEmpty()) {
            return null;
        }
        String normalized = organization.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("组织名称不能超过200个字符");
        }
        return normalized;
    }
}
