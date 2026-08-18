package com.match.agent.web;

import com.match.agent.model.RegistrationTokenRequest;
import com.match.agent.service.RegistrationTokenService;
import com.match.entity.User;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/super-admin/processing-agents")
public class SuperAdminAgentController {
    private final RoleGuard roleGuard;
    private final RegistrationTokenService tokenService;

    public SuperAdminAgentController(RoleGuard roleGuard, RegistrationTokenService tokenService) {
        this.roleGuard = roleGuard;
        this.tokenService = tokenService;
    }

    @PostMapping("/registration-tokens")
    public ResponseResult<Object> createToken(@RequestBody(required = false) RegistrationTokenRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        String label = request == null ? null : request.getLabel();
        return Response.makeOKRsp(tokenService.create(actor.getUserId(), label));
    }

    @GetMapping("/registration-tokens")
    public ResponseResult<Object> registrationTokens() {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(tokenService.list());
    }
}
