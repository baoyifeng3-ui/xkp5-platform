package com.match.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.match.security.AdminAccessException;
import com.match.security.CompetitionAccessException;
import com.match.service.impl.SubmissionValidationException;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ResponseResult<Object>> handleNotLogin() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Response.makeRsp(401, "登录已失效，请重新登录"));
    }

    @ExceptionHandler(AdminAccessException.class)
    public ResponseEntity<ResponseResult<Object>> handleAdminAccess(AdminAccessException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.makeRsp(403, exception.getMessage()));
    }

    @ExceptionHandler(CompetitionAccessException.class)
    public ResponseEntity<ResponseResult<Object>> handleCompetitionAccess(CompetitionAccessException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.makeRsp(403, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseResult<Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.makeRsp(400, exception.getMessage()));
    }

    @ExceptionHandler(SubmissionValidationException.class)
    public ResponseEntity<ResponseResult<Object>> handleSubmissionValidation(SubmissionValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.makeRsp(400, exception.getMessage(),
                        Collections.singletonMap("subjectIds", exception.getSubjectIds())));
    }
}
