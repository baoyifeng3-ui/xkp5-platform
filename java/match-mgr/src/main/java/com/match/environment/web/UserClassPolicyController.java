package com.match.environment.web;

import com.match.environment.service.ActiveClassSessionService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/class-policy")
public class UserClassPolicyController {
    private final RoleGuard roles;
    private final ActiveClassSessionService service;

    public UserClassPolicyController(RoleGuard roles, ActiveClassSessionService service) {
        this.roles = roles;
        this.service = service;
    }

    @GetMapping
    public ResponseResult<Object> get() {
        return Response.makeOKRsp(service.userPolicy(roles.requireUser().getUserId()));
    }
}
