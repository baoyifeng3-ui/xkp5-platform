package com.match.security;

import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.springframework.stereotype.Component;

@Component
public class RoleGuard {
    private final UserServiceImpl userService;
    private final LoginSession loginSession;

    public RoleGuard(UserServiceImpl userService, LoginSession loginSession) {
        this.userService = userService;
        this.loginSession = loginSession;
    }

    public User requireSuperAdmin() {
        return require(UserRole.SUPER_ADMIN, "仅超级管理员可以执行此操作");
    }

    public User requireBusinessAdmin() {
        return require(UserRole.ADMIN, "仅普通管理员可以执行此操作");
    }

    public User requireUser() {
        return require(UserRole.USER, "仅普通用户可以执行此操作");
    }

    public User requireAnyAdmin() {
        User user = currentEnabledUser("仅管理员可以执行此操作");
        UserRole role = roleOf(user);
        if (role != UserRole.SUPER_ADMIN && role != UserRole.ADMIN) {
            throw new AdminAccessException("仅管理员可以执行此操作");
        }
        requireChangedPassword(user);
        return user;
    }

    public UserRole roleOf(User user) {
        return UserRole.resolve(user.getRole(), user.getIsAdmin(), user.getUserName());
    }

    private User require(UserRole requiredRole, String deniedMessage) {
        User user = currentEnabledUser(deniedMessage);
        if (roleOf(user) != requiredRole) {
            throw new AdminAccessException(deniedMessage);
        }
        requireChangedPassword(user);
        return user;
    }

    private User currentEnabledUser(String deniedMessage) {
        User user = userService.getById(loginSession.loginId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new AdminAccessException(deniedMessage);
        }
        return user;
    }

    private void requireChangedPassword(User user) {
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new AdminAccessException("请先修改管理员初始密码");
        }
    }
}
