package com.match.licensing.model;

import java.time.Instant;

public final class LicensePayload {
    private final int version;
    private final String licenseId;
    private final String keyId;
    private final String product;
    private final int supportedMajorVersion;
    private final String requestId;
    private final String installationId;
    private final String challenge;
    private final String fingerprint;
    private final String environment;
    private final String organization;
    private final Instant notBefore;
    private final Instant expiresAt;
    private final Integer maxProcessingServers;
    private final Instant issuedAt;

    public LicensePayload(int version, String licenseId, String keyId, String product,
                          int supportedMajorVersion, String requestId, String installationId,
                          String challenge, String fingerprint, String environment,
                          String organization, Instant notBefore, Instant expiresAt,
                          Integer maxProcessingServers, Instant issuedAt) {
        this.version = version;
        this.licenseId = licenseId;
        this.keyId = keyId;
        this.product = product;
        this.supportedMajorVersion = supportedMajorVersion;
        this.requestId = requestId;
        this.installationId = installationId;
        this.challenge = challenge;
        this.fingerprint = fingerprint;
        this.environment = environment;
        this.organization = organization;
        this.notBefore = notBefore;
        this.expiresAt = expiresAt;
        this.maxProcessingServers = maxProcessingServers;
        this.issuedAt = issuedAt;
    }

    public int getVersion() { return version; }
    public String getLicenseId() { return licenseId; }
    public String getKeyId() { return keyId; }
    public String getProduct() { return product; }
    public int getSupportedMajorVersion() { return supportedMajorVersion; }
    public String getRequestId() { return requestId; }
    public String getInstallationId() { return installationId; }
    public String getChallenge() { return challenge; }
    public String getFingerprint() { return fingerprint; }
    public String getEnvironment() { return environment; }
    public String getOrganization() { return organization; }
    public Instant getNotBefore() { return notBefore; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getMaxProcessingServers() { return maxProcessingServers; }
    public Instant getIssuedAt() { return issuedAt; }
}
