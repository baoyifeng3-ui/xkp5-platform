package com.match.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.match.entity.User;
import com.match.service.impl.ParticipantLoginGate;
import com.match.service.impl.UserServiceImpl;
import com.match.security.PasswordCodec;
import com.match.security.ParticipantModeGuard;
import com.match.mode.model.PlatformModeView;
import com.match.mode.service.PlatformModeService;
import org.junit.Test;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class UserControllerTest {
    @Test
    public void constructorIncludesActivityService() throws Exception {
        UserController.class.getConstructor(
                UserServiceImpl.class, ParticipantLoginGate.class, PasswordCodec.class,
                com.match.dashboard.service.UserActivityService.class,
                PlatformModeService.class, ParticipantModeGuard.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void currentUserDataIncludesCanonicalRole() throws Exception {
        UserController controller = new UserController(
                mock(UserServiceImpl.class),
                mock(ParticipantLoginGate.class),
                mock(PasswordCodec.class),
                mock(com.match.dashboard.service.UserActivityService.class),
                mock(PlatformModeService.class),
                mock(ParticipantModeGuard.class));
        User user = new User();
        user.setUserId(9);
        user.setUserName("manager");
        user.setRole("ADMIN");
        user.setEnabled(true);

        PlatformModeView mode = new PlatformModeView("TRAINING", 7L, 1,
                LocalDateTime.of(2026, 8, 20, 4, 5));
        Method method = UserController.class.getDeclaredMethod(
                "currentUserData", User.class, PlatformModeView.class);
        method.setAccessible(true);
        Map<String, Object> data = (Map<String, Object>) method.invoke(controller, user, mode);

        assertEquals("ADMIN", data.get("role"));
        assertEquals(true, data.get("admin"));
        assertEquals("TRAINING", data.get("platformMode"));
        assertEquals(7L, data.get("modeGeneration"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void currentUserDataIncludesModeForEveryCanonicalRole() throws Exception {
        PlatformModeService modeService = mock(PlatformModeService.class);
        ParticipantModeGuard modeGuard = mock(ParticipantModeGuard.class);
        UserController controller = new UserController(
                mock(UserServiceImpl.class), mock(ParticipantLoginGate.class),
                mock(PasswordCodec.class),
                mock(com.match.dashboard.service.UserActivityService.class),
                modeService, modeGuard);
        Method method = UserController.class.getDeclaredMethod(
                "currentUserData", User.class, PlatformModeView.class);
        method.setAccessible(true);
        PlatformModeView mode = new PlatformModeView("COMPETITION", 12L, 1,
                LocalDateTime.of(2026, 8, 20, 4, 5));

        for (String role : new String[]{"USER", "ADMIN", "SUPER_ADMIN"}) {
            User user = new User();
            user.setUserId(9);
            user.setUserName(role.toLowerCase());
            user.setRole(role);
            Map<String, Object> data = (Map<String, Object>) method.invoke(controller, user, mode);
            assertEquals(role, data.get("role"));
            assertEquals("COMPETITION", data.get("platformMode"));
            assertEquals(12L, data.get("modeGeneration"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void participantLoginReturnsAndBindsCurrentModeGeneration() {
        UserServiceImpl userService = mock(UserServiceImpl.class);
        ParticipantLoginGate loginGate = mock(ParticipantLoginGate.class);
        PasswordCodec passwordCodec = mock(PasswordCodec.class);
        com.match.dashboard.service.UserActivityService activityService =
                mock(com.match.dashboard.service.UserActivityService.class);
        PlatformModeService modeService = mock(PlatformModeService.class);
        ParticipantModeGuard modeGuard = mock(ParticipantModeGuard.class);
        User participant = loginUser("USER");
        when(userService.getOne(any())).thenReturn(participant);
        when(passwordCodec.matches("secret", "stored-password")).thenReturn(true);
        when(modeService.current()).thenReturn(new PlatformModeView("COMPETITION", 12L, 1,
                LocalDateTime.of(2026, 8, 20, 4, 5)));

        StpLogic original = StpUtil.stpLogic;
        StpLogic stpLogic = mock(StpLogic.class);
        SaTokenInfo tokenInfo = mock(SaTokenInfo.class);
        when(stpLogic.getTokenInfo()).thenReturn(tokenInfo);
        when(stpLogic.getTokenTimeout()).thenReturn(3600L);
        when(tokenInfo.getTokenName()).thenReturn("satoken");
        when(tokenInfo.getTokenValue()).thenReturn("participant-token");
        when(tokenInfo.getLoginId()).thenReturn(21);
        StpUtil.setStpLogic(stpLogic);
        try {
            UserController controller = new UserController(userService, loginGate, passwordCodec,
                    activityService, modeService, modeGuard);
            User input = new User();
            input.setUserName("participant");
            input.setPassword("secret");

            Map<String, Object> data = (Map<String, Object>) controller.login(input).getData();

            assertEquals("COMPETITION", data.get("platformMode"));
            assertEquals(12L, data.get("modeGeneration"));
            verify(modeGuard).bindCurrentParticipantSession(12L);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    public void administratorLoginReturnsModeWithoutBindingParticipantGeneration() {
        PlatformModeService modeService = mock(PlatformModeService.class);
        ParticipantModeGuard modeGuard = mock(ParticipantModeGuard.class);
        User administrator = loginUser("ADMIN");
        UserServiceImpl userService = mock(UserServiceImpl.class);
        PasswordCodec passwordCodec = mock(PasswordCodec.class);
        when(userService.getOne(any())).thenReturn(administrator);
        when(passwordCodec.matches("secret", "stored-password")).thenReturn(true);
        when(modeService.current()).thenReturn(new PlatformModeView("TRAINING", 6L, 1,
                LocalDateTime.of(2026, 8, 20, 4, 5)));

        StpLogic original = StpUtil.stpLogic;
        StpLogic stpLogic = mock(StpLogic.class);
        SaTokenInfo tokenInfo = mock(SaTokenInfo.class);
        when(stpLogic.getTokenInfo()).thenReturn(tokenInfo);
        when(stpLogic.getTokenTimeout()).thenReturn(3600L);
        when(tokenInfo.getTokenValue()).thenReturn("admin-token");
        StpUtil.setStpLogic(stpLogic);
        try {
            UserController controller = new UserController(userService,
                    mock(ParticipantLoginGate.class), passwordCodec,
                    mock(com.match.dashboard.service.UserActivityService.class),
                    modeService, modeGuard);
            User input = new User();
            input.setUserName("administrator");
            input.setPassword("secret");

            Map data = (Map) controller.login(input).getData();

            assertEquals("TRAINING", data.get("platformMode"));
            assertEquals(6L, data.get("modeGeneration"));
            verify(modeGuard, never()).bindCurrentParticipantSession(org.mockito.ArgumentMatchers.anyLong());
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    private User loginUser(String role) {
        User user = new User();
        user.setUserId(21);
        user.setUserName("USER".equals(role) ? "participant" : "administrator");
        user.setPassword("stored-password");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
