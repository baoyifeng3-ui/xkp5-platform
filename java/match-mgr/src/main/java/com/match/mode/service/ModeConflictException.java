package com.match.mode.service;

public class ModeConflictException extends RuntimeException {
    private final String code;

    public ModeConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
