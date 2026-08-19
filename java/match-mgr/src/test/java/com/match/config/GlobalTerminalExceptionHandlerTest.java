package com.match.config;

import com.match.terminal.service.TerminalSessionException;
import com.match.util.result.ResponseResult;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GlobalTerminalExceptionHandlerTest {
    @Test
    public void usesOperatorResponseConventionAndStableReasonCode() {
        TerminalSessionException exception = new TerminalSessionException(
                "TERMINAL_SESSION_NOT_FOUND", "Terminal session was not found", HttpStatus.NOT_FOUND);

        ResponseEntity<ResponseResult<Object>> response =
                new GlobalExceptionHandler().handleTerminalSession(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
        assertEquals("Terminal session was not found", response.getBody().getMsg());
        assertEquals("TERMINAL_SESSION_NOT_FOUND",
                ((Map) response.getBody().getData()).get("reasonCode"));
    }
}
