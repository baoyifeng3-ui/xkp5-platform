package com.match.security;

import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoleGuardTest {
    private UserServiceImpl userService;
    private LoginSession loginSession;
    private RoleGuard guard;

    @Before
    public void setUp() {
        userService = mock(UserServiceImpl.class);
        loginSession = mock(LoginSession.class);
        guard = new RoleGuard(userService, loginSession);
    }

    @Test
    public void normalAdminCannotUseSuperAdminGuard() {
        User user = enabledUser("manager", "ADMIN");
        when(userService.getById(7)).thenReturn(user);
        when(loginSession.loginId()).thenReturn(7);
        try {
            guard.requireSuperAdmin();
            fail("normal administrator must not enter operations");
        } catch (AdminAccessException expected) {
            assertEquals("仅超级管理员可以执行此操作", expected.getMessage());
        }
    }

    @Test
    public void superAdminCannotUseBusinessAdminGuard() {
        User user = enabledUser("admin", "SUPER_ADMIN");
        when(userService.getById(1)).thenReturn(user);
        when(loginSession.loginId()).thenReturn(1);
        try {
            guard.requireBusinessAdmin();
            fail("super administrator must not enter business management");
        } catch (AdminAccessException expected) {
            assertEquals("仅普通管理员可以执行此操作", expected.getMessage());
        }
    }

    private User enabledUser(String userName, String role) {
        User user = new User();
        user.setUserName(userName);
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        return user;
    }
}
