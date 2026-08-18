package com.match.agent.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = AgentController.class)
public class AgentProtocolExceptionHandler {
    @ExceptionHandler(AgentProtocolException.class)
    public ResponseEntity<Map<String, String>> handle(AgentProtocolException exception) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", exception.getCode());
        response.put("message", exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(response);
    }
}
