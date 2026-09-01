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
import java.util.List;
import java.util.Map;

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
        User actor = adminGuard.requireAdmin();
        try {
            return Response.makeOKRsp(adminUserManagementService.createBatch(request, actor.getUserId()));
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
        User actor = adminGuard.requireAdmin();
        return Response.makeOKRsp(adminUserManagementService.create(request, actor.getUserId()));
    }

    @PostMapping("bulk-enable")
    public ResponseResult<Object> bulkEnable(@RequestBody Map<String, List<Integer>> request) {
        adminGuard.requireAdmin(); return Response.makeOKRsp(
                adminUserManagementService.setEnabled(request.get("userIds"), true));
    }

    @PostMapping("bulk-disable")
    public ResponseResult<Object> bulkDisable(@RequestBody Map<String, List<Integer>> request) {
        adminGuard.requireAdmin(); return Response.makeOKRsp(
                adminUserManagementService.setEnabled(request.get("userIds"), false));
    }

    @PostMapping("bulk-delete")
    public ResponseResult<Object> bulkDelete(@RequestBody Map<String, List<Integer>> request) {
        adminGuard.requireAdmin(); return Response.makeOKRsp(
                adminUserManagementService.deleteUsers(request.get("userIds")));
    }

    @DeleteMapping("{userId}")
    public ResponseResult<Object> delete(@PathVariable Integer userId) {
        adminGuard.requireAdmin(); return Response.makeOKRsp(
                adminUserManagementService.deleteUsers(java.util.Collections.singletonList(userId)));
    }

    @PutMapping("{userId}")
    public ResponseResult<Object> update(@PathVariable Integer userId,
                                         @RequestBody AdminUserRequest request) {
        User currentAdmin = adminGuard.requireAdmin();
        if (userId.equals(currentAdmin.getUserId())
                && (Boolean.FALSE.equals(request.getEnabled()) || Boolean.FALSE.equals(request.getAdmin()))) {
            return Response.makeRsp(400, "不能停用或取消当前登录管理员权限");
        }
        return Response.makeOKRsp(adminUserManagementService.update(userId, request, currentAdmin.getUserId()));
    }
}
