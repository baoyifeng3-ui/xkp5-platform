package com.match.controller;

import com.match.dto.AdminUserBatchRequest;
import com.match.dto.AdminUserRequest;
import com.match.entity.User;
import com.match.security.AdminGuard;
import com.match.service.impl.AdminUserManagementService;
import com.match.util.result.ResponseResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminUserControllerTest {
    @Mock
    private AdminUserManagementService adminUserManagementService;

    @Mock
    private AdminGuard adminGuard;

    private AdminUserController controller;
    private User currentAdmin;

    @Before
    public void setUp() {
        controller = new AdminUserController(adminUserManagementService, adminGuard);
        currentAdmin = new User();
        currentAdmin.setUserId(1);
        currentAdmin.setUserName("colleague");
        currentAdmin.setEnabled(true);
        currentAdmin.setIsAdmin(true);
        when(adminGuard.requireAdmin()).thenReturn(currentAdmin);
    }

    @Test
    public void preventsCurrentAdminFromDisablingSelf() {
        AdminUserRequest request = new AdminUserRequest();
        request.setUserName("colleague");
        request.setEnabled(false);

        ResponseResult<Object> result = controller.update(1, request);

        assertEquals(400, result.getCode());
        assertEquals("不能停用或取消当前登录管理员权限", result.getMsg());
    }

    @Test
    public void preventsCurrentAdminFromRemovingOwnRole() {
        AdminUserRequest request = new AdminUserRequest();
        request.setUserName("colleague");
        request.setAdmin(false);

        ResponseResult<Object> result = controller.update(1, request);

        assertEquals(400, result.getCode());
        assertEquals("不能停用或取消当前登录管理员权限", result.getMsg());
    }

    @Test
    public void reportsBatchUserNameConflicts() {
        AdminUserBatchRequest request = new AdminUserBatchRequest();
        request.setCount(20);
        when(adminUserManagementService.createBatch(request, 1))
                .thenThrow(new DuplicateKeyException("duplicate user name"));

        ResponseResult<Object> result = controller.createBatch(request);

        assertEquals(400, result.getCode());
        assertEquals("账号序号冲突，请重试", result.getMsg());
    }
}
