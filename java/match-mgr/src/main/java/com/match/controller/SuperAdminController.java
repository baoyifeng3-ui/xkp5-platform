package com.match.controller;

import com.match.dto.AdministratorRequest;
import com.match.security.RoleGuard;
import com.match.service.impl.AdministratorManagementService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/super-admin/administrators")
public class SuperAdminController {
    private final AdministratorManagementService administratorService;
    private final RoleGuard roleGuard;

    public SuperAdminController(AdministratorManagementService administratorService, RoleGuard roleGuard) {
        this.administratorService = administratorService;
        this.roleGuard = roleGuard;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(administratorService.list());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody AdministratorRequest request) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(administratorService.create(request));
    }

    @PutMapping("{userId}")
    public ResponseResult<Object> update(@PathVariable Integer userId,
                                         @RequestBody AdministratorRequest request) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(administratorService.update(userId, request));
    }

    @PostMapping("{userId}/reset-password")
    public ResponseResult<Object> resetPassword(@PathVariable Integer userId,
                                                @RequestBody AdministratorRequest request) {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(administratorService.resetPassword(userId, request));
    }
}
