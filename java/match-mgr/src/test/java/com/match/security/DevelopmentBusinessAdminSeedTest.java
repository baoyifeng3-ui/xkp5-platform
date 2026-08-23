package com.match.security;

import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DevelopmentBusinessAdminSeedTest {
    @Test
    public void seedsBusinessAdminWithAdminRole() throws Exception {
        UserServiceImpl service = mock(UserServiceImpl.class);
        new DevelopmentBusinessAdminSeed(service, "admin", "admin").run(null);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(service).saveOrUpdate(saved.capture());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertEquals("admin", saved.getValue().getUserName());
    }
}
