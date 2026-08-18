package com.match.licensing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class LicenseSignatureVerifier {
    private static final Set<String> ENVELOPE_FIELDS = new HashSet<>(
            Arrays.asList("formatVersion", "keyId", "payload", "signature"));

    private final StrictJson strictJson;
    private final CanonicalJson canonicalJson;

    public LicenseSignatureVerifier(StrictJson strictJson, CanonicalJson canonicalJson) {
        this.strictJson = strictJson;
        this.canonicalJson = canonicalJson;
    }

    public JsonNode verify(String content, Map<String, String> publicKeys) {
        JsonNode envelope = strictJson.readTree(content);
        validateEnvelope(envelope);

        String keyId = requiredText(envelope, "keyId");
        JsonNode payload = envelope.get("payload");
        if (payload == null || !payload.isObject()) {
            throw invalid("INVALID_PAYLOAD", "授权文件载荷无效");
        }
        if (!keyId.equals(requiredText(payload, "keyId"))) {
            throw invalid("KEY_ID_MISMATCH", "授权文件密钥标识不一致");
        }
        String encodedKey = publicKeys == null ? null : publicKeys.get(keyId);
        if (encodedKey == null) {
            throw invalid("UNKNOWN_KEY", "授权文件使用了未知密钥");
        }

        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519", new BouncyCastleProvider());
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance("Ed25519", new BouncyCastleProvider());
            signature.initVerify(publicKey);
            signature.update(canonicalJson.writeBytes(payload));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(requiredText(envelope, "signature"));
            if (!signature.verify(signatureBytes)) {
                throw invalid("INVALID_SIGNATURE", "授权文件签名无效");
            }
            return payload.deepCopy();
        } catch (InvalidLicenseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidLicenseException("INVALID_SIGNATURE", "授权文件签名无效", exception);
        }
    }

    private void validateEnvelope(JsonNode envelope) {
        JsonNode formatVersion = envelope.get("formatVersion");
        if (!envelope.isObject() || formatVersion == null || !formatVersion.isIntegralNumber()
                || !formatVersion.canConvertToInt() || formatVersion.intValue() != 1) {
            throw invalid("UNSUPPORTED_FORMAT", "不支持的授权文件版本");
        }
        Set<String> actualFields = new HashSet<>();
        Iterator<String> names = envelope.fieldNames();
        while (names.hasNext()) {
            actualFields.add(names.next());
        }
        if (!actualFields.equals(ENVELOPE_FIELDS)) {
            throw invalid("INVALID_ENVELOPE", "授权文件外层字段无效");
        }
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw invalid("INVALID_ENVELOPE", "授权文件缺少字段: " + field);
        }
        return value.asText();
    }

    private InvalidLicenseException invalid(String code, String message) {
        return new InvalidLicenseException(code, message);
    }
}
