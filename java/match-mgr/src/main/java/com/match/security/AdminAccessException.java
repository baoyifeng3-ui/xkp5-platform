package com.match.security;

public class AdminAccessException extends RuntimeException {
    public AdminAccessException(String message) {
        super(message);
    }
}
