package com.match.agent.web;

import com.match.terminal.service.TerminalSessionException;
import com.match.terminal.web.AgentTerminalController;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {AgentController.class, AgentTerminalController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentProtocolExceptionHandler {
    @ExceptionHandler(AgentProtocolException.class)
    public ResponseEntity<Map<String, String>> handle(AgentProtocolException exception) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", exception.getCode());
        response.put("message", exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(response);
    }

    @ExceptionHandler(TerminalSessionException.class)
    public ResponseEntity<Map<String, String>> handleTerminal(TerminalSessionException exception) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", exception.getCode());
        response.put("message", exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(response);
    }
}
