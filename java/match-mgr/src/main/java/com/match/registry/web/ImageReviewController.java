package com.match.registry.web;

import com.match.registry.dto.ImageReviewRequest;
import com.match.registry.service.ImageUploadService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/image-artifacts")
public class ImageReviewController {
    private final RoleGuard roleGuard; private final ImageUploadService service;
    public ImageReviewController(RoleGuard roleGuard, ImageUploadService service) { this.roleGuard = roleGuard; this.service = service; }
    @PostMapping("/{artifactId}/review") public ResponseResult<Object> review(@PathVariable String artifactId, @RequestBody ImageReviewRequest request) { com.match.entity.User u = roleGuard.requireSuperAdmin(); return Response.makeOKRsp(service.review("SUPER_ADMIN", artifactId, request, u.getUserId())); }
}
