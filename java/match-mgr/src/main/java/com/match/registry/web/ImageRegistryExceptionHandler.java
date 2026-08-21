package com.match.registry.web;

import com.match.registry.service.ImageUploadService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {ImageUploadController.class, ImageReviewController.class})
public class ImageRegistryExceptionHandler {
    @ExceptionHandler(ImageUploadService.UploadException.class)
    public ResponseResult<Map<String, String>> uploadError(ImageUploadService.UploadException error) {
        return Response.makeRsp(400, error.getMessage(), Collections.singletonMap("reasonCode", error.getCode()));
    }
}
