package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.service.EnvironmentOperationService;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrainingEnvironmentControllerTest {
    @Test
    public void administratorLifecycleActionsUseTheAuthenticatedRole() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        User administrator = user(9);
        when(roleGuard.requireAnyAdmin()).thenReturn(administrator);
        when(roleGuard.roleOf(administrator)).thenReturn(UserRole.ADMIN);
        AdminTrainingEnvironmentController controller =
                new AdminTrainingEnvironmentController(roleGuard, service);

        controller.start("environment-1");
        controller.stop("environment-1");
        controller.restore("environment-1");

        verify(service).start("environment-1", 9, "ADMIN");
        verify(service).stop("environment-1", 9, "ADMIN");
        verify(service).restore("environment-1", 9, "ADMIN");
    }

    @Test
    public void normalUserSurfaceListsAndStartsOnlyForAuthenticatedUser() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        EnvironmentOperationService service = mock(EnvironmentOperationService.class);
        User student = user(21);
        when(roleGuard.requireUser()).thenReturn(student);
        UserTrainingEnvironmentController controller =
                new UserTrainingEnvironmentController(roleGuard, service);

        controller.list();
        controller.start("environment-1");

        verify(service).listForUser(21);
        verify(service).start("environment-1", 21, "USER");
    }

    private User user(int id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
