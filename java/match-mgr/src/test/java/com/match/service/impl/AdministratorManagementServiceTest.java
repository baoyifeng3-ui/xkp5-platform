package com.match.service.impl;

import com.match.dto.AdministratorRequest;
import com.match.dto.AdministratorView;
import com.match.entity.User;
import com.match.mapper.UserMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdministratorManagementServiceTest {
    private UserMapper userMapper;
    private AdministratorManagementService service;

    @Before
    public void setUp() {
        userMapper = mock(UserMapper.class);
        service = new AdministratorManagementService(userMapper);
    }

    @Test
    public void createsOnlyNormalAdministrators() {
        AdministratorRequest request = request("manager", "secret1");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.<User>getArgument(0).setUserId(8);
            return 1;
        });

        AdministratorView view = service.create(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());

        assertEquals("ADMIN", captor.getValue().getRole());
        assertTrue(captor.getValue().getIsAdmin());
        assertTrue(view.getMustChangePassword());
    }

    @Test
    public void listsOnlyRoleAdminAccounts() {
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(
                user(1, "admin", "SUPER_ADMIN"),
                user(2, "manager", "ADMIN"),
                user(3, "student", "USER")));

        List<AdministratorView> result = service.list();

        assertEquals(1, result.size());
        assertEquals("manager", result.get(0).getUserName());
    }

    @Test
    public void preventsEditingCurrentSuperAdministrator() {
        when(userMapper.selectById(1)).thenReturn(user(1, "admin", "SUPER_ADMIN"));
        try {
            service.update(1, request("admin", null));
            fail("super administrator must not be edited through ordinary admin service");
        } catch (IllegalArgumentException expected) {
            assertEquals("普通管理员账号不存在", expected.getMessage());
        }
    }

    @Test
    public void resetsAdministratorPasswordAndRequiresChange() {
        User manager = user(2, "manager", "ADMIN");
        manager.setMustChangePassword(false);
        when(userMapper.selectById(2)).thenReturn(manager);

        service.resetPassword(2, request(null, "newpass"));

        assertEquals("newpass", manager.getPassword());
        assertTrue(manager.getMustChangePassword());
    }

    @Test
    public void neverReturnsStoredPasswords() {
        User manager = user(2, "manager", "ADMIN");
        manager.setPassword("stored-secret");
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(manager));

        AdministratorView view = service.list().get(0);

        for (Field field : view.getClass().getDeclaredFields()) {
            assertFalse("password".equalsIgnoreCase(field.getName()));
        }
        assertNull(findPasswordField(view));
    }

    private Object findPasswordField(AdministratorView view) {
        try {
            return view.getClass().getMethod("getPassword").invoke(view);
        } catch (ReflectiveOperationException expected) {
            return null;
        }
    }

    private AdministratorRequest request(String userName, String password) {
        AdministratorRequest request = new AdministratorRequest();
        request.setUserName(userName);
        request.setPassword(password);
        return request;
    }

    private User user(int id, String name, String role) {
        User user = new User();
        user.setUserId(id);
        user.setUserName(name);
        user.setRole(role);
        user.setEnabled(true);
        user.setIsAdmin(!"USER".equals(role));
        return user;
    }
}
