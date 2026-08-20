package com.match.licensing.guard;

public class LicenseAccessException extends RuntimeException {
    private final String reasonCode;

    public LicenseAccessException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
