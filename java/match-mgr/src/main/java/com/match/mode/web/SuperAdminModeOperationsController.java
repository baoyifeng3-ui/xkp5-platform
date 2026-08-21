package com.match.mode.web;

import com.match.entity.User;
import com.match.mode.service.ModeTransitionService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operations/mode-transitions")
public class SuperAdminModeOperationsController {
    private final RoleGuard roleGuard;
    private final ModeTransitionService transitionService;

    public SuperAdminModeOperationsController(RoleGuard roleGuard,
                                              ModeTransitionService transitionService) {
        this.roleGuard = roleGuard;
        this.transitionService = transitionService;
    }

    @GetMapping("/{transitionId}")
    public ResponseResult<Object> transition(@PathVariable String transitionId) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(transitionService.get(transitionId));
    }

    @PostMapping("/{transitionId}/retry")
    public ResponseResult<Object> retry(@PathVariable String transitionId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(transitionService.retry(transitionId, actor));
    }
}
