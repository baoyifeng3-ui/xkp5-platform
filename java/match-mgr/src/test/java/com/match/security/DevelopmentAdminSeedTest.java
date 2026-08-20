package com.match.security;

import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

public class DevelopmentAdminSeedTest {
    @Test
    public void seedsLocalSuperAdminWithConfiguredCredentials() throws Exception {
        UserServiceImpl service = mock(UserServiceImpl.class);
        DevelopmentAdminSeed seed = new DevelopmentAdminSeed(service, "admin123", "admin123");

        seed.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(service).saveOrUpdate(captor.capture());
        User saved = captor.getValue();
        assertEquals("admin123", saved.getUserName());
        assertEquals("admin123", saved.getPassword());
        assertTrue(saved.getEnabled());
        assertTrue(saved.getIsAdmin());
        assertEquals("SUPER_ADMIN", saved.getRole());
    }

    @Test
    public void blankConfigurationDoesNotWriteAnAccount() throws Exception {
        UserServiceImpl service = mock(UserServiceImpl.class);
        DevelopmentAdminSeed seed = new DevelopmentAdminSeed(service, " ", "");

        seed.run(null);

        verify(service, never()).saveOrUpdate(any(User.class));
    }
}
