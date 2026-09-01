package com.match.transfer;

import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/training-models")
public class ModelWorkspaceController {
    private final RoleGuard roles;
    private final ModelWorkspaceService service;

    public ModelWorkspaceController(RoleGuard roles, ModelWorkspaceService service) {
        this.roles = roles;
        this.service = service;
    }

    @PostMapping("/environments/{environmentId}/files")
    public ResponseResult<Object> list(@PathVariable String environmentId) {
        User user = roles.requireBusinessUser();
        return Response.makeOKRsp(service.list(user.getUserId(), roles.roleOf(user) == UserRole.ADMIN, environmentId));
    }

    @PostMapping("/environments/{environmentId}/deploy")
    public ResponseResult<Object> deploy(@PathVariable String environmentId,
                                         @RequestBody ModelWorkspaceRequest request) {
        User user = roles.requireBusinessUser();
        return Response.makeOKRsp(service.deploy(user.getUserId(), roles.roleOf(user) == UserRole.ADMIN,
                environmentId, request));
    }

    @GetMapping("/commands/{commandId}")
    public ResponseResult<Object> status(@PathVariable String commandId) {
        User user = roles.requireBusinessUser();
        return Response.makeOKRsp(service.status(user.getUserId(), roles.roleOf(user) == UserRole.ADMIN, commandId));
    }
}
