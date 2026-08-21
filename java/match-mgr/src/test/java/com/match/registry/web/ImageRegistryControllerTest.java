package com.match.registry.web;

import com.match.registry.service.ImageUploadService;
import com.match.security.RoleGuard;
import org.junit.Test;
import java.lang.reflect.Method;
import org.springframework.web.bind.annotation.*;
import static org.junit.Assert.*;

public class ImageRegistryControllerTest {
    @Test public void routesKeepWritesOnSuperAdminController() throws Exception { assertEquals("/admin/image-uploads", ImageUploadController.class.getAnnotation(RequestMapping.class).value()[0]); Method create = ImageUploadController.class.getMethod("create", com.match.registry.dto.ImageUploadCreateRequest.class); assertNotNull(create.getAnnotation(PostMapping.class)); assertNotNull(ImageReviewController.class.getMethod("review", String.class, com.match.registry.dto.ImageReviewRequest.class).getAnnotation(PostMapping.class)); }
    @Test public void serviceExposesStableReasonCodes() { assertEquals("CHECKSUM_MISMATCH", ImageUploadService.CODE_CHECKSUM); assertEquals("INVALID_DOCKER_ARCHIVE", ImageUploadService.CODE_ARCHIVE); }
    @Test public void exceptionHandlerReturnsStableReasonCode() { ImageRegistryExceptionHandler handler = new ImageRegistryExceptionHandler(); org.springframework.http.ResponseEntity<com.match.util.result.ResponseResult<java.util.Map<String,String>>> response = handler.uploadError(new ImageUploadService.UploadException("CHECKSUM_MISMATCH", "bad")); assertEquals(400, response.getStatusCodeValue()); assertEquals("CHECKSUM_MISMATCH", response.getBody().getData().get("reasonCode")); }
}
