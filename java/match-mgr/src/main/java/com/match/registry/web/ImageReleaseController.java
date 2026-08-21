package com.match.registry.web;

import com.match.entity.User;
import com.match.registry.persistence.ImageDeploymentRecord;
import com.match.registry.persistence.ImageReleaseRecord;
import com.match.registry.service.ImageDeploymentService;
import com.match.registry.service.ImageReleaseService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/image-releases")
public class ImageReleaseController {
    private final RoleGuard roleGuard;
    private final ImageReleaseService releases;
    private final ImageDeploymentService deployments;

    public ImageReleaseController(RoleGuard roleGuard, ImageReleaseService releases,
                                  ImageDeploymentService deployments) {
        this.roleGuard = roleGuard; this.releases = releases; this.deployments = deployments;
    }

    @PostMapping("/publish/{artifactId}")
    public ResponseResult<Object> publish(@PathVariable String artifactId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(releases.publish("SUPER_ADMIN", artifactId, actor.getUserId()));
    }

    @GetMapping("/{releaseId}")
    public ResponseResult<Object> release(@PathVariable String releaseId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(releases.release(roleGuard.roleOf(actor).name(), releaseId));
    }

    @PostMapping("/{releaseId}/deploy")
    public ResponseResult<Object> deploy(@PathVariable String releaseId, @RequestBody DeploymentRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        if (request == null) throw new IllegalArgumentException("deployment request is required");
        ImageDeploymentRecord result = deployments.deploy("SUPER_ADMIN", releaseId, request.agentId,
                request.componentType, request.updatePolicy, request.idempotencyKey,
                request.confirm, actor.getUserId());
        return Response.makeOKRsp(result);
    }

    @GetMapping("/deployments/{deploymentId}")
    public ResponseResult<Object> deployment(@PathVariable String deploymentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(deployments.status(roleGuard.roleOf(actor).name(), deploymentId));
    }

    @PostMapping("/deployments/{deploymentId}/rollback")
    public ResponseResult<Object> rollback(@PathVariable String deploymentId,
                                           @RequestParam(defaultValue = "false") boolean confirm) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(deployments.rollback("SUPER_ADMIN", deploymentId, confirm, actor.getUserId()));
    }

    public static class DeploymentRequest {
        public String agentId;
        public String componentType;
        public String updatePolicy;
        public String idempotencyKey;
        public boolean confirm;
    }
}
