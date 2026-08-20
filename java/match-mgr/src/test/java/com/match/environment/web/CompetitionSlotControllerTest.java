package com.match.environment.web;

import com.match.entity.User;
import com.match.environment.model.BindCompetitionSlotRequest;
import com.match.environment.service.CompetitionSlotBindingService;
import com.match.security.AdminAccessException;
import com.match.security.LoginSession;
import com.match.security.RoleGuard;
import com.match.service.impl.UserServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompetitionSlotControllerTest {
    private static final String SLOT_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void adminControllerUsesExactBusinessAdminBoundary() {
        assertAdminBoundary("ADMIN", true);
        assertAdminDenied("SUPER_ADMIN", true);
        assertAdminDenied("USER", true);
        assertAdminDenied("ADMIN", false);
    }

    @Test
    public void bindingAndUnbindingUseAuthenticatedAdminAndRequestedTargetUser() {
        Fixture fixture = fixture("ADMIN", true, 7);
        BindCompetitionSlotRequest request = new BindCompetitionSlotRequest();
        request.setUserId(21);

        fixture.adminController.bind(SLOT_ID, request);
        fixture.adminController.unbind(SLOT_ID, 21);

        verify(fixture.service).bind(SLOT_ID, 21, fixture.current);
        verify(fixture.service).unbind(SLOT_ID, 21, fixture.current);
    }

    @Test
    public void participantControllerNeverTrustsCallerSuppliedUserId() {
        Fixture fixture = fixture("USER", true, 21);

        assertEquals(200, fixture.userController.current().getCode());

        verify(fixture.service).currentForUser(fixture.current);
    }

    @Test
    public void administratorsCannotReadParticipantCompetitionEndpoint() {
        Fixture fixture = fixture("ADMIN", true, 7);
        try {
            fixture.userController.current();
            fail("ADMIN must not use USER endpoint");
        } catch (AdminAccessException expected) {
            assertEquals("仅普通用户可以执行此操作", expected.getMessage());
        }
    }

    private void assertAdminBoundary(String role, boolean enabled) {
        Fixture fixture = fixture(role, enabled, 7);
        assertEquals(200, fixture.adminController.list().getCode());
        verify(fixture.service).list(fixture.current);
    }

    private void assertAdminDenied(String role, boolean enabled) {
        Fixture fixture = fixture(role, enabled, 7);
        try {
            fixture.adminController.list();
            fail(role + " must not use ADMIN slot endpoint");
        } catch (AdminAccessException expected) {
            assertEquals("仅普通管理员可以执行此操作", expected.getMessage());
        }
    }

    private Fixture fixture(String role, boolean enabled, int id) {
        User current = new User();
        current.setUserId(id);
        current.setUserName(role.toLowerCase());
        current.setRole(role);
        current.setEnabled(enabled);
        current.setMustChangePassword(false);
        UserServiceImpl users = mock(UserServiceImpl.class);
        LoginSession session = mock(LoginSession.class);
        when(session.loginId()).thenReturn(id);
        when(users.getById(id)).thenReturn(current);
        RoleGuard guard = new RoleGuard(users, session);
        CompetitionSlotBindingService service = mock(CompetitionSlotBindingService.class);
        return new Fixture(current, service,
                new AdminCompetitionSlotController(guard, service),
                new UserCompetitionEnvironmentController(guard, service));
    }

    private static final class Fixture {
        private final User current;
        private final CompetitionSlotBindingService service;
        private final AdminCompetitionSlotController adminController;
        private final UserCompetitionEnvironmentController userController;

        private Fixture(User current, CompetitionSlotBindingService service,
                        AdminCompetitionSlotController adminController,
                        UserCompetitionEnvironmentController userController) {
            this.current = current;
            this.service = service;
            this.adminController = adminController;
            this.userController = userController;
        }
    }
}
