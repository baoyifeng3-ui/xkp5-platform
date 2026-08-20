package com.match.licensing.web;

public class LicenseRequestInput {
    private String organization;

    public LicenseRequestInput() {
    }

    public LicenseRequestInput(String organization) {
        this.organization = organization;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }
}
