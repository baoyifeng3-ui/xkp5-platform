package com.match.agent.web;

import org.springframework.http.HttpStatus;

public class AgentProtocolException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AgentProtocolException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
