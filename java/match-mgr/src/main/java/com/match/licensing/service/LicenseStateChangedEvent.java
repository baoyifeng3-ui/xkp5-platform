package com.match.licensing.service;

import com.match.licensing.model.LicenseState;

public final class LicenseStateChangedEvent {
    private final LicenseState previousState;
    private final LicenseState currentState;
    private final String licenseId;

    public LicenseStateChangedEvent(LicenseState previousState, LicenseState currentState, String licenseId) {
        this.previousState = previousState;
        this.currentState = currentState;
        this.licenseId = licenseId;
    }

    public LicenseState getPreviousState() { return previousState; }
    public LicenseState getCurrentState() { return currentState; }
    public String getLicenseId() { return licenseId; }
}
