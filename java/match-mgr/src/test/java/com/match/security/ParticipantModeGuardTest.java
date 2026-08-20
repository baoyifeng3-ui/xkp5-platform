package com.match.security;

import cn.dev33.satoken.session.SaSession;
import com.match.entity.User;
import com.match.mode.model.PlatformModeView;
import com.match.mode.service.PlatformModeService;
import com.match.service.impl.UserServiceImpl;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParticipantModeGuardTest {
    private UserServiceImpl userService;
    private RoleGuard roleGuard;
    private LoginSession loginSession;
    private PlatformModeService modeService;
    private SaSession tokenSession;
    private ParticipantModeGuard guard;

    @Before
    public void setUp() {
        userService = mock(UserServiceImpl.class);
        roleGuard = mock(RoleGuard.class);
        loginSession = mock(LoginSession.class);
        modeService = mock(PlatformModeService.class);
        tokenSession = new SaSession("token-session");
        when(loginSession.loginId()).thenReturn(21);
        when(loginSession.tokenSession()).thenReturn(tokenSession);
        guard = new ParticipantModeGuard(userService, roleGuard, loginSession, modeService);
    }

    @Test
    public void bindsGenerationToCurrentSaTokenSessionWithExactKey() {
        guard.bindCurrentParticipantSession(12L);

        assertEquals(12L, tokenSession.get("participantModeGeneration"));
    }

    @Test
    public void matchingParticipantGenerationRemainsAuthenticated() {
        participant();
        tokenSession.set("participantModeGeneration", 12L);
        currentMode("COMPETITION", 12L);

        guard.requireCurrentGeneration();

        verify(loginSession, never()).logout();
    }

    @Test
    public void staleOrMissingParticipantGenerationLogsOutAndRejects() {
        participant();
        currentMode("TRAINING", 13L);
        tokenSession.set("participantModeGeneration", 12L);

        expect("PLATFORM_MODE_CHANGED", "平台模式已切换，请重新登录",
                guard::requireCurrentGeneration);
        verify(loginSession).logout();

        tokenSession.delete("participantModeGeneration");
        expect("PLATFORM_MODE_CHANGED", "平台模式已切换，请重新登录",
                guard::requireCurrentGeneration);
        verify(loginSession, org.mockito.Mockito.times(2)).logout();
    }

    @Test
    public void nonParticipantGenerationChecksBypassModeLookupAndTokenSession() {
        User administrator = enabledUser("ADMIN");
        when(userService.getById(21)).thenReturn(administrator);
        when(roleGuard.roleOf(administrator)).thenReturn(UserRole.ADMIN);

        guard.requireCurrentGeneration();

        verify(modeService, never()).current();
        verify(loginSession, never()).tokenSession();
        verify(loginSession, never()).logout();
    }

    @Test
    public void participantTrainingAndCompetitionModesAreBidirectionallyIsolated() {
        participant();
        currentMode("TRAINING", 7L);
        guard.requireTrainingMode();
        expect("WRONG_PLATFORM_MODE", "当前平台模式不允许访问此功能",
                guard::requireCompetitionMode);

        currentMode("COMPETITION", 8L);
        guard.requireCompetitionMode();
        expect("WRONG_PLATFORM_MODE", "当前平台模式不允许访问此功能",
                guard::requireTrainingMode);
    }

    @Test
    public void nonParticipantEndpointModeChecksBypassPlatformMode() {
        User administrator = enabledUser("ADMIN");
        when(userService.getById(21)).thenReturn(administrator);
        when(roleGuard.roleOf(administrator)).thenReturn(UserRole.ADMIN);

        guard.requireTrainingMode();
        guard.requireCompetitionMode();

        verify(modeService, never()).current();
    }

    @Test
    public void administrativeTrainingModeUsesAuthenticatedRoleGuardAndCurrentMode() {
        currentMode("TRAINING", 4L);
        guard.requireAdministrativeTrainingMode();
        verify(roleGuard).requireAnyAdmin();

        currentMode("COMPETITION", 5L);
        expect("WRONG_PLATFORM_MODE", "当前平台模式不允许访问此功能",
                guard::requireAdministrativeTrainingMode);
        verify(roleGuard, org.mockito.Mockito.times(2)).requireAnyAdmin();
    }

    private void participant() {
        User participant = enabledUser("USER");
        when(userService.getById(21)).thenReturn(participant);
        when(roleGuard.roleOf(participant)).thenReturn(UserRole.USER);
    }

    private User enabledUser(String role) {
        User user = new User();
        user.setUserId(21);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private void currentMode(String mode, long generation) {
        when(modeService.current()).thenReturn(new PlatformModeView(mode, generation, 9,
                LocalDateTime.of(2026, 8, 20, 4, 5)));
    }

    private void expect(String reasonCode, String message, Runnable action) {
        try {
            action.run();
            fail("expected ParticipantModeException");
        } catch (ParticipantModeException exception) {
            assertEquals(reasonCode, exception.getReasonCode());
            assertEquals(message, exception.getMessage());
        }
    }
}
