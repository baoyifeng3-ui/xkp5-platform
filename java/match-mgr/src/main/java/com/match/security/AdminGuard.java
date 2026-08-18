package com.match.security;

import com.match.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AdminGuard {
    private final RoleGuard roleGuard;

    public AdminGuard(RoleGuard roleGuard) {
        this.roleGuard = roleGuard;
    }

    public User requireAdmin() {
        return roleGuard.requireBusinessAdmin();
    }
}
