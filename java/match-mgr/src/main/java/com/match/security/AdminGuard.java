package com.match.security;

import cn.dev33.satoken.stp.StpUtil;
import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminGuard {
    private final UserServiceImpl userService;
    private final String adminUserName;

    public AdminGuard(UserServiceImpl userService,
                      @Value("${match.admin-user-name:admin}") String adminUserName) {
        this.userService = userService;
        this.adminUserName = adminUserName;
    }

    public User requireAdmin() {
        StpUtil.checkLogin();
        User user = userService.getById(StpUtil.getLoginIdAsInt());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !isAdmin(user)) {
            throw new AdminAccessException("仅管理员可以执行此操作");
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new AdminAccessException("请先修改管理员初始密码");
        }
        return user;
    }

    private boolean isAdmin(User user) {
        return Boolean.TRUE.equals(user.getIsAdmin()) || adminUserName.equalsIgnoreCase(user.getUserName());
    }
}
