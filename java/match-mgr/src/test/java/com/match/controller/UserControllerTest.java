package com.match.controller;

import com.match.entity.User;
import com.match.service.impl.ParticipantLoginGate;
import com.match.service.impl.UserServiceImpl;
import com.match.security.PasswordCodec;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UserControllerTest {
    @Test
    @SuppressWarnings("unchecked")
    public void currentUserDataIncludesCanonicalRole() throws Exception {
        UserController controller = new UserController(
                mock(UserServiceImpl.class),
                mock(ParticipantLoginGate.class),
                mock(PasswordCodec.class));
        User user = new User();
        user.setUserId(9);
        user.setUserName("manager");
        user.setRole("ADMIN");
        user.setEnabled(true);

        Method method = UserController.class.getDeclaredMethod("currentUserData", User.class);
        method.setAccessible(true);
        Map<String, Object> data = (Map<String, Object>) method.invoke(controller, user);

        assertEquals("ADMIN", data.get("role"));
        assertEquals(true, data.get("admin"));
    }
}
