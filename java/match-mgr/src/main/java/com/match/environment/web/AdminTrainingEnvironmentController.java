package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/training-environments")
public class AdminTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final EnvironmentOperationService operationService;

    public AdminTrainingEnvironmentController(RoleGuard roleGuard,
                                              EnvironmentOperationService operationService) {
        this.roleGuard = roleGuard;
        this.operationService = operationService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.listAll());
    }

    @PostMapping("/{environmentId}/start")
    public ResponseResult<Object> start(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.start(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{environmentId}/stop")
    public ResponseResult<Object> stop(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.stop(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }

    @PostMapping("/{environmentId}/restore")
    public ResponseResult<Object> restore(@PathVariable String environmentId) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.restore(environmentId, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
    }
}
