package com.match.registry.web;

import com.match.registry.service.ImageUploadService;
import com.match.security.RoleGuard;
import com.match.entity.User;
import org.junit.Test;
import java.lang.reflect.Method;
import org.springframework.web.bind.annotation.*;
import static org.junit.Assert.*;

public class ImageRegistryControllerTest {
    @Test public void agentArchiveUsesAuthenticatedInternalRedirectInsteadOfJavaFileStreaming() throws Exception {
        com.match.agent.service.AgentCredentialService credentials = org.mockito.Mockito.mock(com.match.agent.service.AgentCredentialService.class);
        com.match.registry.service.AgentImageArchiveService archives = org.mockito.Mockito.mock(com.match.registry.service.AgentImageArchiveService.class);
        com.match.agent.persistence.ProcessingAgentRecord agent = new com.match.agent.persistence.ProcessingAgentRecord(); agent.setAgentId("agent-1");
        org.mockito.Mockito.when(credentials.authenticate("Bearer credential")).thenReturn(agent);
        org.mockito.Mockito.when(archives.internalLocation("agent-1", "deployment-1"))
                .thenReturn("/_protected/image-archives/files/xkp/code.tar");
        org.springframework.http.ResponseEntity<Void> response = new AgentImageDeploymentController(credentials, archives)
                .archive("Bearer credential", "deployment-1");
        assertEquals("/_protected/image-archives/files/xkp/code.tar", response.getHeaders().getFirst("X-Accel-Redirect"));
        assertNull(response.getBody());
        org.mockito.Mockito.verify(credentials).authenticate("Bearer credential");
    }
    @Test public void routesKeepWritesOnSuperAdminController() throws Exception { assertEquals("/admin/image-uploads", ImageUploadController.class.getAnnotation(RequestMapping.class).value()[0]); Method create = ImageUploadController.class.getMethod("create", com.match.registry.dto.ImageUploadCreateRequest.class); assertNotNull(create.getAnnotation(PostMapping.class)); assertNotNull(ImageReviewController.class.getMethod("review", String.class, com.match.registry.dto.ImageReviewRequest.class).getAnnotation(PostMapping.class)); }
    @Test public void serviceExposesStableReasonCodes() { assertEquals("CHECKSUM_MISMATCH", ImageUploadService.CODE_CHECKSUM); assertEquals("INVALID_DOCKER_ARCHIVE", ImageUploadService.CODE_ARCHIVE); }
    @Test public void exceptionHandlerReturnsStableReasonCode() { ImageRegistryExceptionHandler handler = new ImageRegistryExceptionHandler(); org.springframework.http.ResponseEntity<com.match.util.result.ResponseResult<java.util.Map<String,String>>> response = handler.uploadError(new ImageUploadService.UploadException("CHECKSUM_MISMATCH", "bad")); assertEquals(400, response.getStatusCodeValue()); assertEquals("CHECKSUM_MISMATCH", response.getBody().getData().get("reasonCode")); }
    @Test public void completeSubmitsBackgroundCompletionInsteadOfMergingInHttpRequest() {
        RoleGuard roles = org.mockito.Mockito.mock(RoleGuard.class); ImageUploadService uploads = org.mockito.Mockito.mock(ImageUploadService.class);
        User actor = new User(); actor.setUserId(7); org.mockito.Mockito.when(roles.requireSuperAdmin()).thenReturn(actor);
        ImageUploadController controller = new ImageUploadController(roles, uploads);
        controller.complete("upload-1");
        org.mockito.Mockito.verify(uploads).requestCompletion("SUPER_ADMIN", "upload-1");
        org.mockito.Mockito.verify(uploads, org.mockito.Mockito.never()).complete("SUPER_ADMIN", "upload-1");
    }
}
