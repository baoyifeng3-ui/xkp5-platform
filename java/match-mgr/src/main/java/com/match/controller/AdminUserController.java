package com.match.controller;

import com.match.dto.AdminUserBatchRequest;
import com.match.dto.AdminUserRequest;
import com.match.entity.User;
import com.match.security.AdminGuard;
import com.match.service.impl.AdminUserManagementService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserManagementService adminUserManagementService;
    private final AdminGuard adminGuard;

    public AdminUserController(AdminUserManagementService adminUserManagementService,
                               AdminGuard adminGuard) {
        this.adminUserManagementService = adminUserManagementService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(adminUserManagementService.listUsers());
    }

    @PostMapping("batch")
    public ResponseResult<Object> createBatch(@RequestBody AdminUserBatchRequest request) {
        adminGuard.requireAdmin();
        try {
            return Response.makeOKRsp(adminUserManagementService.createBatch(request.getCount()));
        } catch (DuplicateKeyException exception) {
            return Response.makeRsp(400, "账号序号冲突，请重试");
        }
    }

    @DeleteMapping("participants")
    public ResponseResult<Object> clearParticipants() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(adminUserManagementService.clearParticipants());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody AdminUserRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(adminUserManagementService.create(request));
    }

    @PutMapping("{userId}")
    public ResponseResult<Object> update(@PathVariable Integer userId,
                                         @RequestBody AdminUserRequest request) {
        User currentAdmin = adminGuard.requireAdmin();
        if (userId.equals(currentAdmin.getUserId())
                && (Boolean.FALSE.equals(request.getEnabled()) || Boolean.FALSE.equals(request.getAdmin()))) {
            return Response.makeRsp(400, "不能停用或取消当前登录管理员权限");
        }
        return Response.makeOKRsp(adminUserManagementService.update(userId, request));
    }
}
