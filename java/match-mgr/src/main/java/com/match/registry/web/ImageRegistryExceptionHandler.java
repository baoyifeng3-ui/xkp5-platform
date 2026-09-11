package com.match.registry.web;

import com.match.registry.service.ImageUploadService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {ImageUploadController.class, ImageReviewController.class, ImageFileController.class})
public class ImageRegistryExceptionHandler {
    @ExceptionHandler(ImageUploadService.UploadException.class)
    public ResponseEntity<ResponseResult<Map<String, String>>> uploadError(ImageUploadService.UploadException error) {
        Map<String, String> data = new java.util.LinkedHashMap<>(); data.put("reasonCode", error.getCode()); if (error.getReferenceId() != null) data.put("referenceId", error.getReferenceId());
        return ResponseEntity.badRequest().body(Response.makeRsp(400, error.getMessage(), data));
    }
}
