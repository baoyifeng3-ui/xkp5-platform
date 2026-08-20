package com.match.controller;

import com.match.dto.AdministratorRequest;
import com.match.security.RoleGuard;
import com.match.service.impl.AdministratorManagementService;
import org.junit.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class SuperAdminControllerTest {
    @Test
    public void checksSuperAdminBeforeCreatingAdministrator() {
        AdministratorManagementService service = mock(AdministratorManagementService.class);
        RoleGuard roleGuard = mock(RoleGuard.class);
        SuperAdminController controller = new SuperAdminController(service, roleGuard);
        AdministratorRequest request = new AdministratorRequest();
        request.setUserName("manager");
        request.setPassword("secret1");

        controller.create(request);

        org.mockito.InOrder order = inOrder(roleGuard, service);
        order.verify(roleGuard).requireSuperAdmin();
        order.verify(service).create(request);
    }
}
