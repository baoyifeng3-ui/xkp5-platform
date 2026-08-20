package com.match.licensing.identity;

public final class HostIdentity {
    private final String fingerprint;
    private final String environment;

    public HostIdentity(String fingerprint, String environment) {
        this.fingerprint = fingerprint;
        this.environment = environment;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getEnvironment() {
        return environment;
    }
}
