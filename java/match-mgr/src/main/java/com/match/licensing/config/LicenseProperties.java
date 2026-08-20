package com.match.licensing.config;

import com.match.licensing.crypto.InvalidLicenseException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "xkp.license")
public class LicenseProperties {
    private String publicKeys = "";
    private String testPublicKey = "";

    public String getPublicKeys() {
        return publicKeys;
    }

    public void setPublicKeys(String publicKeys) {
        this.publicKeys = publicKeys == null ? "" : publicKeys;
    }

    public String getTestPublicKey() {
        return testPublicKey;
    }

    public void setTestPublicKey(String testPublicKey) {
        this.testPublicKey = testPublicKey == null ? "" : testPublicKey;
    }

    public Map<String, String> productionKeys() {
        return parse(publicKeys, false);
    }

    public Map<String, String> developmentKeys() {
        return parse(testPublicKey, true);
    }

    private Map<String, String> parse(String configured, boolean singleEntry) {
        Map<String, String> result = new LinkedHashMap<>();
        if (configured == null || configured.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        String[] entries = configured.split(",", -1);
        if (singleEntry && entries.length != 1) {
            throw invalid("开发环境只能配置一个测试公钥");
        }
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw invalid("授权公钥配置格式必须为 keyId=base64X509");
            }
            String keyId = entry.substring(0, separator).trim();
            String key = entry.substring(separator + 1).trim();
            if (keyId.isEmpty() || key.isEmpty() || result.containsKey(keyId)) {
                throw invalid("授权公钥标识为空或重复");
            }
            result.put(keyId, key);
        }
        return Collections.unmodifiableMap(result);
    }

    private InvalidLicenseException invalid(String message) {
        return new InvalidLicenseException("INVALID_KEY_CONFIGURATION", message);
    }
}
