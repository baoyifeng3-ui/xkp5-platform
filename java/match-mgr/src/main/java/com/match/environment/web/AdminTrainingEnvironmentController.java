package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.environment.service.TrainingEnvironmentService;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/training-environments")
public class AdminTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final EnvironmentOperationService operationService;
    private final TrainingEnvironmentService environmentService;

    public AdminTrainingEnvironmentController(RoleGuard roleGuard,
                                              EnvironmentOperationService operationService,
                                              TrainingEnvironmentService environmentService) {
        this.roleGuard = roleGuard;
        this.operationService = operationService;
        this.environmentService = environmentService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(operationService.listAll());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CreateTrainingEnvironmentRequest request) {
        User actor = roleGuard.requireAnyAdmin();
        return Response.makeOKRsp(environmentService.create(request, actor.getUserId(),
                roleGuard.roleOf(actor).name()));
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
