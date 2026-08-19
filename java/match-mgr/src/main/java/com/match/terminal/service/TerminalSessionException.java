package com.match.terminal.service;

public class TerminalSessionException extends RuntimeException {
    private final String code;

    public TerminalSessionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
