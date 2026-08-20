package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import com.match.environment.service.TrainingEnvironmentService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/super-admin/training-environments")
public class SuperAdminTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final TrainingEnvironmentService environmentService;

    public SuperAdminTrainingEnvironmentController(RoleGuard roleGuard,
                                                   TrainingEnvironmentService environmentService) {
        this.roleGuard = roleGuard;
        this.environmentService = environmentService;
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CreateTrainingEnvironmentRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(environmentService.create(request, actor.getUserId(), "SUPER_ADMIN"));
    }
}
