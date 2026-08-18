package com.match.licensing.crypto;

public class InvalidLicenseException extends RuntimeException {
    private final String code;

    public InvalidLicenseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public InvalidLicenseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
