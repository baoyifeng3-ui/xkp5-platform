package com.match.registry.web;

import com.match.registry.service.ImageFileService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.match.registry.service.ImageDeploymentService;
import com.match.registry.service.ImageUploadService;
import com.match.registry.persistence.ImageDeploymentRecord;

@RestController
@RequestMapping("/admin/image-files")
public class ImageFileController {
    private final RoleGuard roles;
    private final ImageFileService files;
    private final ImageDeploymentService deployments;
    private final ImageUploadService uploads;

    public ImageFileController(RoleGuard roles, ImageFileService files, ImageDeploymentService deployments, ImageUploadService uploads) { this.roles = roles; this.files = files; this.deployments = deployments; this.uploads = uploads; }

    @GetMapping public ResponseResult<Object> list(@RequestParam(defaultValue = "200") int limit) {
        roles.requireAnyAdmin(); return Response.makeOKRsp(files.list(limit));
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseResult<Object> upload(@org.springframework.web.bind.annotation.RequestParam(required = false) String repository,
                                         @org.springframework.web.bind.annotation.RequestParam(required = false) String tag,
                                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean overwrite,
                                         @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        com.match.entity.User actor = roles.requireSuperAdmin();
        return Response.makeOKRsp(uploads.uploadFile("SUPER_ADMIN", repository, tag, overwrite, file, actor.getUserId()));
    }

    @DeleteMapping("/{fileId}") public ResponseResult<Object> delete(@PathVariable String fileId) {
        roles.requireSuperAdmin(); files.delete(fileId); return Response.makeOKRsp(null);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{fileId}/deploy")
    public ResponseResult<Object> deploy(@PathVariable String fileId, @org.springframework.web.bind.annotation.RequestBody DeployRequest request) {
        com.match.entity.User actor = roles.requireSuperAdmin();
        if (request == null) throw new IllegalArgumentException("下发请求不能为空");
        ImageDeploymentRecord result = deployments.deployFile("SUPER_ADMIN", fileId, request.agentId,
                request.overwrite, request.idempotencyKey, actor.getUserId());
        return Response.makeOKRsp(result);
    }

    public static class DeployRequest { public String agentId; public boolean overwrite; public String idempotencyKey; }
}
