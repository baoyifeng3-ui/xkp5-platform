package com.match.security;

import com.match.entity.User;
import com.match.mode.model.PlatformModeView;
import com.match.mode.service.PlatformModeService;
import com.match.service.impl.UserServiceImpl;
import org.springframework.stereotype.Component;

@Component
public class ParticipantModeGuard {
    static final String GENERATION_SESSION_KEY = "participantModeGeneration";

    private final UserServiceImpl userService;
    private final RoleGuard roleGuard;
    private final LoginSession loginSession;
    private final PlatformModeService modeService;

    public ParticipantModeGuard(UserServiceImpl userService, RoleGuard roleGuard,
                                LoginSession loginSession, PlatformModeService modeService) {
        this.userService = userService;
        this.roleGuard = roleGuard;
        this.loginSession = loginSession;
        this.modeService = modeService;
    }

    public void bindCurrentParticipantSession(long generation) {
        loginSession.tokenSession().set(GENERATION_SESSION_KEY, generation);
    }

    public void requireCurrentGeneration() {
        if (!currentUserIsParticipant()) {
            return;
        }
        PlatformModeView current = modeService.current();
        Object bound = loginSession.tokenSession().get(GENERATION_SESSION_KEY);
        if (!(bound instanceof Number)
                || ((Number) bound).longValue() != current.getGeneration()) {
            loginSession.logout();
            throw ParticipantModeException.generationMismatch();
        }
    }

    public void requireTrainingMode() {
        requireParticipantMode(PlatformModeService.TRAINING);
    }

    public void requireCompetitionMode() {
        requireParticipantMode(PlatformModeService.COMPETITION);
    }

    public void requireAdministrativeTrainingMode() {
        roleGuard.requireAnyAdmin();
        requireMode(PlatformModeService.TRAINING);
    }

    private void requireParticipantMode(String expectedMode) {
        if (currentUserIsParticipant()) {
            requireMode(expectedMode);
        }
    }

    private void requireMode(String expectedMode) {
        if (!expectedMode.equals(modeService.current().getMode())) {
            throw ParticipantModeException.wrongMode();
        }
    }

    private boolean currentUserIsParticipant() {
        User user = userService.getById(loginSession.loginId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            loginSession.logout();
            throw ParticipantModeException.generationMismatch();
        }
        return roleGuard.roleOf(user) == UserRole.USER;
    }
}
