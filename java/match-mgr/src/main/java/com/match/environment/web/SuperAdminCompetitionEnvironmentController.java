package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.CreateCompetitionEnvironmentRequest;
import com.match.environment.service.CompetitionEnvironmentService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/super-admin/competition-environments")
public class SuperAdminCompetitionEnvironmentController {
    private final RoleGuard roleGuard;
    private final CompetitionEnvironmentService environmentService;

    public SuperAdminCompetitionEnvironmentController(RoleGuard roleGuard,
                                                      CompetitionEnvironmentService environmentService) {
        this.roleGuard = roleGuard;
        this.environmentService = environmentService;
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody CreateCompetitionEnvironmentRequest request) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(environmentService.create(request, actor));
    }

    @PostMapping("/{environmentId}/restore")
    public ResponseResult<Object> restore(@PathVariable String environmentId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(environmentService.restore(environmentId, actor));
    }

    @GetMapping
    public ResponseResult<Object> list() {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(environmentService.list(actor));
    }

    @GetMapping("/{environmentId}")
    public ResponseResult<Object> get(@PathVariable String environmentId) {
        User actor = roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(environmentService.get(environmentId, actor));
    }
}
