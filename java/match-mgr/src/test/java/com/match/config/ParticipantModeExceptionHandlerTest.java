package com.match.config;

import com.match.security.ParticipantModeException;
import com.match.util.result.ResponseResult;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ParticipantModeExceptionHandlerTest {
    @Test
    public void generationMismatchMapsToStableUnauthorizedResponse() {
        ResponseEntity<ResponseResult<Object>> response = new GlobalExceptionHandler()
                .handleParticipantMode(ParticipantModeException.generationMismatch());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getCode());
        assertEquals("平台模式已切换，请重新登录", response.getBody().getMsg());
        assertEquals("PLATFORM_MODE_CHANGED",
                ((Map) response.getBody().getData()).get("reasonCode"));
    }

    @Test
    public void wrongModeMapsToForbiddenResponse() {
        ResponseEntity<ResponseResult<Object>> response = new GlobalExceptionHandler()
                .handleParticipantMode(ParticipantModeException.wrongMode());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
        assertEquals("当前平台模式不允许访问此功能", response.getBody().getMsg());
    }
}
