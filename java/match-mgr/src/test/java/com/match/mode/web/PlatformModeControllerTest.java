package com.match.mode.web;

import com.match.entity.User;
import com.match.mode.model.ChangePlatformModeRequest;
import com.match.mode.service.ModeTransitionService;
import com.match.mode.service.PlatformModeService;
import com.match.security.AdminAccessException;
import com.match.security.LoginSession;
import com.match.security.RoleGuard;
import com.match.service.impl.UserServiceImpl;
import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlatformModeControllerTest {
    @Test
    public void adminReadsAndChangesModeWithExactConfirmations() {
        Fixture fixture = fixture("ADMIN");
        ChangePlatformModeRequest enter = request("COMPETITION", "ENTER COMPETITION");
        ChangePlatformModeRequest exit = request("TRAINING", "EXIT COMPETITION");

        assertEquals(200, fixture.admin.current().getCode());
        assertEquals(200, fixture.admin.change(enter).getCode());
        assertEquals(200, fixture.admin.change(exit).getCode());
        assertEquals(200, fixture.admin.transitions(999).getCode());
        assertEquals(200, fixture.admin.transition("transition-1").getCode());

        verify(fixture.platformModes).current();
        verify(fixture.platformModes).change("COMPETITION", fixture.actor);
        verify(fixture.platformModes).change("TRAINING", fixture.actor);
        verify(fixture.transitions).list(100);
        verify(fixture.transitions).get("transition-1");
    }

    @Test
    public void adminRejectsMissingOrWrongConfirmationBeforeChangingMode() {
        Fixture fixture = fixture("ADMIN");

        assertInvalid(() -> fixture.admin.change(request("COMPETITION", "EXIT COMPETITION")));
        assertInvalid(() -> fixture.admin.change(request("TRAINING", "ENTER COMPETITION")));
        assertInvalid(() -> fixture.admin.change(null));

        verify(fixture.platformModes, never()).change(anyString(), any(User.class));
    }

    @Test
    public void superAdminInspectsAndRetriesButCannotUseAdminEndpoint() {
        Fixture fixture = fixture("SUPER_ADMIN");

        assertEquals(200, fixture.operations.transition("transition-1").getCode());
        assertEquals(200, fixture.operations.retry("transition-1").getCode());
        verify(fixture.transitions).get("transition-1");
        verify(fixture.transitions).retry("transition-1", fixture.actor);

        assertForbidden(() -> fixture.admin.current());
        assertForbidden(() -> fixture.admin.change(request("COMPETITION", "ENTER COMPETITION")));
        assertForbidden(() -> fixture.admin.transitions(10));
        assertForbidden(() -> fixture.admin.transition("transition-1"));
    }

    @Test
    public void adminCannotUseOperationalInspectionOrRetry() {
        Fixture fixture = fixture("ADMIN");

        assertForbidden(() -> fixture.operations.transition("transition-1"));
        assertForbidden(() -> fixture.operations.retry("transition-1"));
        verify(fixture.transitions, never()).retry(anyString(), any(User.class));
    }

    @Test
    public void userIsForbiddenFromAdminAndOperationsSurfaces() {
        Fixture fixture = fixture("USER");

        assertForbidden(() -> fixture.admin.current());
        assertForbidden(() -> fixture.admin.transitions(10));
        assertForbidden(() -> fixture.operations.transition("transition-1"));
        assertForbidden(() -> fixture.operations.retry("transition-1"));
        verify(fixture.platformModes, never()).current();
        verify(fixture.transitions, never()).retry(anyString(), any(User.class));
    }

    @Test
    public void exposesOnlyTheBoundedModeEndpoints() throws Exception {
        assertEquals("/admin/platform-mode", AdminPlatformModeController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("/operations/mode-transitions", SuperAdminModeOperationsController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        assertPath(AdminPlatformModeController.class, "current", GetMapping.class, "");
        assertPath(AdminPlatformModeController.class, "change", PostMapping.class, "");
        assertPath(AdminPlatformModeController.class, "transitions", GetMapping.class, "/transitions");
        assertPath(AdminPlatformModeController.class, "transition", GetMapping.class,
                "/transitions/{transitionId}");
        assertPath(SuperAdminModeOperationsController.class, "transition", GetMapping.class,
                "/{transitionId}");
        assertPath(SuperAdminModeOperationsController.class, "retry", PostMapping.class,
                "/{transitionId}/retry");
    }

    private Fixture fixture(String role) {
        User actor = new User();
        actor.setUserId(7);
        actor.setUserName(role.toLowerCase());
        actor.setRole(role);
        actor.setEnabled(true);
        actor.setMustChangePassword(false);
        UserServiceImpl users = mock(UserServiceImpl.class);
        LoginSession session = mock(LoginSession.class);
        when(session.loginId()).thenReturn(7);
        when(users.getById(7)).thenReturn(actor);
        RoleGuard roleGuard = new RoleGuard(users, session);
        PlatformModeService platformModes = mock(PlatformModeService.class);
        ModeTransitionService transitions = mock(ModeTransitionService.class);
        return new Fixture(actor, platformModes, transitions,
                new AdminPlatformModeController(roleGuard, platformModes, transitions),
                new SuperAdminModeOperationsController(roleGuard, transitions));
    }

    private ChangePlatformModeRequest request(String target, String confirmation) {
        ChangePlatformModeRequest request = new ChangePlatformModeRequest();
        request.setTargetMode(target);
        request.setConfirmation(confirmation);
        return request;
    }

    private void assertInvalid(Runnable call) {
        try {
            call.run();
            fail("invalid confirmation was accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("模式切换确认文本不正确", expected.getMessage());
        }
    }

    private void assertForbidden(Runnable call) {
        try {
            call.run();
            fail("role boundary was not enforced");
        } catch (AdminAccessException expected) {
            // Exact messages are owned by RoleGuard and covered in RoleGuardTest.
        }
    }

    private void assertPath(Class<?> controller, String methodName, Class<?> annotation,
                            String expected) throws Exception {
        Method found = null;
        for (Method method : controller.getMethods()) {
            if (method.getName().equals(methodName)) found = method;
        }
        if (annotation == GetMapping.class) {
            assertEquals(expected, found.getAnnotation(GetMapping.class).value()[0]);
        } else {
            assertEquals(expected, found.getAnnotation(PostMapping.class).value()[0]);
        }
    }

    private static final class Fixture {
        private final User actor;
        private final PlatformModeService platformModes;
        private final ModeTransitionService transitions;
        private final AdminPlatformModeController admin;
        private final SuperAdminModeOperationsController operations;

        private Fixture(User actor, PlatformModeService platformModes,
                        ModeTransitionService transitions, AdminPlatformModeController admin,
                        SuperAdminModeOperationsController operations) {
            this.actor = actor;
            this.platformModes = platformModes;
            this.transitions = transitions;
            this.admin = admin;
            this.operations = operations;
        }
    }
}
