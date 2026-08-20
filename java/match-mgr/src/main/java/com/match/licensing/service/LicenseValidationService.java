package com.match.licensing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.match.licensing.config.LicenseProperties;
import com.match.licensing.crypto.Digests;
import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.crypto.LicenseSignatureVerifier;
import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.model.LicensePayload;
import com.match.licensing.persistence.LicenseRequestMapper;
import com.match.licensing.persistence.LicenseRequestRecord;
import com.match.licensing.persistence.PlatformLicenseRecord;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class LicenseValidationService {
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final Set<String> PAYLOAD_FIELDS = new HashSet<>(Arrays.asList(
            "version", "licenseId", "keyId", "product", "supportedMajorVersion", "requestId",
            "installationId", "challenge", "fingerprint", "environment", "organization",
            "notBefore", "expiresAt", "maxProcessingServers", "issuedAt"));

    private final LicenseSignatureVerifier verifier;
    private final LicenseProperties properties;
    private final HostIdentityProvider identityProvider;
    private final InstallationService installationService;
    private final LicenseRequestMapper requestMapper;
    private final Clock clock;

    public LicenseValidationService(LicenseSignatureVerifier verifier, LicenseProperties properties,
                                    HostIdentityProvider identityProvider,
                                    InstallationService installationService,
                                    LicenseRequestMapper requestMapper, Clock clock) {
        this.verifier = verifier;
        this.properties = properties;
        this.identityProvider = identityProvider;
        this.installationService = installationService;
        this.requestMapper = requestMapper;
        this.clock = clock;
    }

    public ValidatedLicense validate(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0 || fileBytes.length > MAX_FILE_BYTES) {
            throw invalid("INVALID_FILE_SIZE", "授权文件大小无效");
        }
        String envelope = decodeUtf8(fileBytes);
        HostIdentity identity = identityProvider.load();
        Map<String, String> keys = "DEVELOPMENT".equals(identity.getEnvironment())
                ? properties.developmentKeys() : properties.productionKeys();
        JsonNode payloadNode = verifier.verify(envelope, keys);
        requireExactFields(payloadNode);
        LicensePayload payload = parse(payloadNode);

        if (payload.getVersion() != 1 || !"XKP5".equals(payload.getProduct())
                || payload.getSupportedMajorVersion() != 5) {
            throw invalid("PRODUCT_MISMATCH", "授权文件不适用于 XKP5.0");
        }
        if (!identity.getEnvironment().equals(payload.getEnvironment())) {
            throw invalid("ENVIRONMENT_MISMATCH", "授权环境与当前平台不一致");
        }
        if (!installationService.installationId().equals(payload.getInstallationId())) {
            throw invalid("INSTALLATION_MISMATCH", "授权文件不属于当前平台安装实例");
        }
        if (!identity.getFingerprint().equals(payload.getFingerprint())) {
            throw invalid("FINGERPRINT_MISMATCH", "授权文件不属于当前管理服务器");
        }

        LicenseRequestRecord request = requestMapper.selectByIdForUpdate(payload.getRequestId());
        if (request == null || request.getConsumedAt() != null) {
            throw invalid("REQUEST_NOT_AVAILABLE", "授权请求不存在或已使用");
        }
        if (!payload.getInstallationId().equals(request.getInstallationId())
                || !payload.getFingerprint().equals(request.getFingerprintDigest())
                || !payload.getEnvironment().equals(request.getEnvironment())
                || !Digests.sha256(payload.getChallenge()).equals(request.getChallengeHash())) {
            throw invalid("REQUEST_MISMATCH", "授权文件与平台请求不匹配");
        }

        Instant now = clock.instant();
        if (!payload.getExpiresAt().isAfter(payload.getNotBefore())
                || now.isBefore(payload.getNotBefore()) || !now.isBefore(payload.getExpiresAt())) {
            throw invalid("LICENSE_TIME_INVALID", "授权文件当前不在有效期内");
        }
        return new ValidatedLicense(payload, envelope, request);
    }

    public LicensePayload validateStored(PlatformLicenseRecord record) {
        if (record == null || record.getSignedEnvelope() == null || record.getSignedEnvelope().trim().isEmpty()) {
            throw invalid("STORED_LICENSE_MISMATCH", "已激活授权记录不完整");
        }
        HostIdentity identity = identityProvider.load();
        Map<String, String> keys = "DEVELOPMENT".equals(identity.getEnvironment())
                ? properties.developmentKeys() : properties.productionKeys();
        JsonNode payloadNode = verifier.verify(record.getSignedEnvelope(), keys);
        requireExactFields(payloadNode);
        LicensePayload payload = parse(payloadNode);
        boolean matches = payload.getVersion() == 1
                && "XKP5".equals(payload.getProduct())
                && payload.getSupportedMajorVersion() == 5
                && Objects.equals(payload.getLicenseId(), record.getLicenseId())
                && Objects.equals(payload.getRequestId(), record.getRequestId())
                && Objects.equals(payload.getOrganization(), record.getOrganization())
                && Objects.equals(payload.getEnvironment(), record.getEnvironment())
                && Objects.equals(payload.getFingerprint(), record.getFingerprintDigest())
                && Objects.equals(payload.getKeyId(), record.getKeyId())
                && Objects.equals(payload.getMaxProcessingServers(), record.getMaxProcessingServers())
                && record.getNotBefore() != null
                && payload.getNotBefore().equals(record.getNotBefore().toInstant(ZoneOffset.UTC))
                && record.getExpiresAt() != null
                && payload.getExpiresAt().equals(record.getExpiresAt().toInstant(ZoneOffset.UTC))
                && Objects.equals(payload.getInstallationId(), installationService.installationId())
                && Objects.equals(payload.getEnvironment(), identity.getEnvironment())
                && Objects.equals(payload.getFingerprint(), identity.getFingerprint());
        if (!matches) {
            throw invalid("STORED_LICENSE_MISMATCH", "已激活授权记录与签名内容不一致");
        }
        return payload;
    }

    private LicensePayload parse(JsonNode node) {
        int version = requiredInt(node, "version");
        int supportedMajorVersion = requiredInt(node, "supportedMajorVersion");
        JsonNode maxNode = node.get("maxProcessingServers");
        Integer maxServers = null;
        if (maxNode != null && !maxNode.isNull()) {
            if (!maxNode.isIntegralNumber() || !maxNode.canConvertToInt() || maxNode.intValue() <= 0) {
                throw invalid("INVALID_PAYLOAD", "处理服务器授权数量无效");
            }
            maxServers = maxNode.intValue();
        }
        return new LicensePayload(version, requiredText(node, "licenseId"), requiredText(node, "keyId"),
                requiredText(node, "product"), supportedMajorVersion, requiredText(node, "requestId"),
                requiredText(node, "installationId"), requiredText(node, "challenge"),
                requiredText(node, "fingerprint"), requiredText(node, "environment"),
                requiredText(node, "organization"), requiredInstant(node, "notBefore"),
                requiredInstant(node, "expiresAt"), maxServers, requiredInstant(node, "issuedAt"));
    }

    private void requireExactFields(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw invalid("INVALID_PAYLOAD", "授权载荷格式无效");
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> fields = payload.fieldNames();
        while (fields.hasNext()) {
            actual.add(fields.next());
        }
        if (!actual.equals(PAYLOAD_FIELDS)) {
            throw invalid("INVALID_PAYLOAD", "授权载荷字段无效");
        }
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("INVALID_PAYLOAD", "授权载荷字段无效: " + field);
        }
        return value.intValue();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw invalid("INVALID_PAYLOAD", "授权载荷字段无效: " + field);
        }
        return value.asText();
    }

    private Instant requiredInstant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field));
        } catch (DateTimeParseException exception) {
            throw invalid("INVALID_PAYLOAD", "授权时间字段无效: " + field);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            CharBuffer value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return value.toString();
        } catch (CharacterCodingException exception) {
            throw invalid("INVALID_ENCODING", "授权文件必须使用 UTF-8 编码");
        }
    }

    private InvalidLicenseException invalid(String code, String message) {
        return new InvalidLicenseException(code, message);
    }

    public static final class ValidatedLicense {
        private final LicensePayload payload;
        private final String signedEnvelope;
        private final LicenseRequestRecord request;

        public ValidatedLicense(LicensePayload payload, String signedEnvelope, LicenseRequestRecord request) {
            this.payload = payload;
            this.signedEnvelope = signedEnvelope;
            this.request = request;
        }

        public LicensePayload getPayload() { return payload; }
        public String getSignedEnvelope() { return signedEnvelope; }
        public LicenseRequestRecord getRequest() { return request; }
    }
}
