package com.match.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.match.security.AdminAccessException;
import com.match.security.CompetitionAccessException;
import com.match.security.ParticipantModeException;
import com.match.licensing.crypto.InvalidLicenseException;
import com.match.licensing.guard.LicenseAccessException;
import com.match.licensing.web.LicenseImportException;
import com.match.mode.service.ModeConflictException;
import com.match.service.impl.SubmissionValidationException;
import com.match.terminal.service.TerminalSessionException;
import com.match.resource.service.ResourceOperationException;
import com.match.environment.service.ClassOfflineException;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @ExceptionHandler(ClassOfflineException.class)
    public ResponseEntity<ResponseResult<Object>> handleClassOffline(ClassOfflineException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonCode", "CLASS_SERVERS_OFFLINE");
        data.put("offlineServers", exception.getOfflineServers());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.makeRsp(409, exception.getMessage(), data));
    }

    @ExceptionHandler(ResourceOperationException.class)
    public ResponseEntity<ResponseResult<Object>> handleResourceOperation(
            ResourceOperationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Response.makeRsp(
                exception.getStatus(), exception.getMessage(), reason(exception.getCode())));
    }

    @ExceptionHandler(TerminalSessionException.class)
    public ResponseEntity<ResponseResult<Object>> handleTerminalSession(TerminalSessionException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Response.makeRsp(exception.getStatus().value(), exception.getMessage(),
                        reason(exception.getCode())));
    }

    @ExceptionHandler(ParticipantModeException.class)
    public ResponseEntity<ResponseResult<Object>> handleParticipantMode(
            ParticipantModeException exception) {
        if (exception.isGenerationMismatch()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Response.makeRsp(401, exception.getMessage(),
                            reason(exception.getReasonCode())));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.makeRsp(403, exception.getMessage()));
    }

    @ExceptionHandler(ModeConflictException.class)
    public ResponseEntity<ResponseResult<Object>> handleModeConflict(ModeConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.makeRsp(409, exception.getMessage(), reason(exception.getCode())));
    }

    @ExceptionHandler(SubmissionValidationException.class)
    public ResponseEntity<ResponseResult<Object>> handleSubmissionValidation(SubmissionValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.makeRsp(400, exception.getMessage(),
                        Collections.singletonMap("subjectIds", exception.getSubjectIds())));
    }

    @ExceptionHandler(InvalidLicenseException.class)
    public ResponseEntity<ResponseResult<Object>> handleInvalidLicense(InvalidLicenseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.makeRsp(400, exception.getMessage(), reason(exception.getReasonCode())));
    }

    @ExceptionHandler(LicenseImportException.class)
    public ResponseEntity<ResponseResult<Object>> handleLicenseImport(LicenseImportException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.makeRsp(400, exception.getMessage(), reason(exception.getReasonCode())));
    }

    @ExceptionHandler(LicenseAccessException.class)
    public ResponseEntity<ResponseResult<Object>> handleLicenseAccess(LicenseAccessException exception) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Response.makeRsp(423, exception.getMessage(), reason(exception.getReasonCode())));
    }

    private Map<String, String> reason(String reasonCode) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("reasonCode", reasonCode);
        return data;
    }
}
