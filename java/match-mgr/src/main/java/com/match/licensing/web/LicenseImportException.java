package com.match.licensing.web;

public class LicenseImportException extends RuntimeException {
    private final String reasonCode;

    public LicenseImportException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
