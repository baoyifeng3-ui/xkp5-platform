package com.match.config;

import com.match.mode.service.ModeConflictException;
import com.match.util.result.ResponseResult;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GlobalModeConflictExceptionHandlerTest {
    @Test
    public void mapsStableModeConflictCodeToHttpConflictResponse() {
        ModeConflictException exception = new ModeConflictException(
                "PLATFORM_MODE_CONFLICT", "Platform mode changed concurrently");

        ResponseEntity<ResponseResult<Object>> response =
                new GlobalExceptionHandler().handleModeConflict(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getCode());
        assertEquals("Platform mode changed concurrently", response.getBody().getMsg());
        assertEquals("PLATFORM_MODE_CONFLICT",
                ((Map) response.getBody().getData()).get("reasonCode"));
    }
}
