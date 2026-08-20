package com.match.licensing.model;

import java.time.Instant;

public final class LicenseStatus {
    private final LicenseState state;
    private final String licenseId;
    private final String organization;
    private final Instant expiresAt;
    private final Integer maxProcessingServers;

    public LicenseStatus(LicenseState state, String licenseId, String organization,
                         Instant expiresAt, Integer maxProcessingServers) {
        this.state = state;
        this.licenseId = licenseId;
        this.organization = organization;
        this.expiresAt = expiresAt;
        this.maxProcessingServers = maxProcessingServers;
    }

    public LicenseState getState() { return state; }
    public String getLicenseId() { return licenseId; }
    public String getOrganization() { return organization; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getMaxProcessingServers() { return maxProcessingServers; }

    public boolean isUsable() {
        return state == LicenseState.ACTIVE || state == LicenseState.EXPIRING;
    }
}
