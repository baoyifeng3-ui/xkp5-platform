package com.match.dashboard.web;

import com.match.dashboard.model.DashboardOverview;
import com.match.dashboard.service.DashboardOverviewService;
import com.match.security.AdminGuard;
import com.match.security.AdminAccessException;
import com.match.security.LoginSession;
import com.match.security.RoleGuard;
import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import com.match.util.result.ResponseResult;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.fail;

public class AdminDashboardControllerTest {
    @Test
    public void overviewRequiresBusinessAdminAndReturnsSnapshot() {
        AdminGuard guard = mock(AdminGuard.class);
        DashboardOverviewService service = mock(DashboardOverviewService.class);
        DashboardOverview snapshot = new DashboardOverview();
        when(service.snapshot()).thenReturn(snapshot);
        AdminDashboardController controller = new AdminDashboardController(guard, service);

        ResponseResult<DashboardOverview> response = controller.overview();

        verify(guard).requireAdmin();
        assertSame(snapshot, response.getData());
    }

    @Test
    public void normalUserIsForbidden() {
        assertForbidden("USER", 21);
    }

    @Test
    public void superAdminIsForbidden() {
        assertForbidden("SUPER_ADMIN", 1);
    }

    private void assertForbidden(String role, int userId) {
        UserServiceImpl users = mock(UserServiceImpl.class);
        LoginSession session = mock(LoginSession.class);
        User user = new User();
        user.setRole(role);
        user.setEnabled(true);
        when(session.loginId()).thenReturn(userId);
        when(users.getById(userId)).thenReturn(user);
        AdminDashboardController controller = new AdminDashboardController(
                new AdminGuard(new RoleGuard(users, session)), mock(DashboardOverviewService.class));
        try {
            controller.overview();
            fail(role + " must not access the business dashboard");
        } catch (AdminAccessException expected) {
            // RoleGuard maps this exception to the existing forbidden response handler.
        }
    }
}
