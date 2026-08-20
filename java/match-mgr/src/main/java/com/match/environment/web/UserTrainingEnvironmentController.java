package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/training-environments")
public class UserTrainingEnvironmentController {
    private final RoleGuard roleGuard;
    private final EnvironmentOperationService operationService;

    public UserTrainingEnvironmentController(RoleGuard roleGuard,
                                             EnvironmentOperationService operationService) {
        this.roleGuard = roleGuard;
        this.operationService = operationService;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        User user = roleGuard.requireUser();
        return Response.makeOKRsp(operationService.listForUser(user.getUserId()));
    }

    @PostMapping("/{environmentId}/start")
    public ResponseResult<Object> start(@PathVariable String environmentId) {
        User user = roleGuard.requireUser();
        return Response.makeOKRsp(operationService.start(environmentId, user.getUserId(), "USER"));
    }
}
