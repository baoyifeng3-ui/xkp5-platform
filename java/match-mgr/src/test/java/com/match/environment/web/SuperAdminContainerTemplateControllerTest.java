package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.ContainerTemplateRequest;
import com.match.environment.service.ContainerTemplateService;
import com.match.security.RoleGuard;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SuperAdminContainerTemplateControllerTest {
    @Test
    public void everyEndpointRequiresSuperAdmin() {
        RoleGuard roles = mock(RoleGuard.class);
        ContainerTemplateService templates = mock(ContainerTemplateService.class);
        User actor = new User();
        actor.setUserId(7);
        when(roles.requireSuperAdmin()).thenReturn(actor);
        SuperAdminContainerTemplateController controller =
                new SuperAdminContainerTemplateController(roles, templates);
        ContainerTemplateRequest request = new ContainerTemplateRequest();

        controller.publish(request);
        controller.disable("template-id", 2);
        controller.list();
        controller.versions("template-id");

        verify(roles, org.mockito.Mockito.times(4)).requireSuperAdmin();
        verify(templates).publish(request, 7);
        verify(templates).disable("template-id", 2, 7);
    }
}
