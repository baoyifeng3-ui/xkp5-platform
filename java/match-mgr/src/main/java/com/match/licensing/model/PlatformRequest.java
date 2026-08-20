package com.match.licensing.model;

public final class PlatformRequest {
    private final int formatVersion;
    private final String requestId;
    private final String installationId;
    private final String challenge;
    private final String fingerprint;
    private final String environment;
    private final String organization;
    private final String platformVersion;
    private final String createdAt;

    public PlatformRequest(int formatVersion, String requestId, String installationId, String challenge,
                           String fingerprint, String environment, String organization,
                           String platformVersion, String createdAt) {
        this.formatVersion = formatVersion;
        this.requestId = requestId;
        this.installationId = installationId;
        this.challenge = challenge;
        this.fingerprint = fingerprint;
        this.environment = environment;
        this.organization = organization;
        this.platformVersion = platformVersion;
        this.createdAt = createdAt;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInstallationId() {
        return installationId;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getOrganization() {
        return organization;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
