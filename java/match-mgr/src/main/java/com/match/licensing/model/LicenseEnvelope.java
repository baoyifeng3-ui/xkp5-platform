package com.match.licensing.model;

public final class LicenseEnvelope {
    private final int formatVersion;
    private final String keyId;
    private final LicensePayload payload;
    private final String signature;

    public LicenseEnvelope(int formatVersion, String keyId, LicensePayload payload, String signature) {
        this.formatVersion = formatVersion;
        this.keyId = keyId;
        this.payload = payload;
        this.signature = signature;
    }

    public int getFormatVersion() { return formatVersion; }
    public String getKeyId() { return keyId; }
    public LicensePayload getPayload() { return payload; }
    public String getSignature() { return signature; }
}
