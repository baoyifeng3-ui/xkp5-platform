package com.match.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserRoleTest {
    @Test
    public void resolvesStoredRolesAndLegacyFallbacks() {
        assertEquals(UserRole.SUPER_ADMIN, UserRole.resolve("SUPER_ADMIN", true, "admin"));
        assertEquals(UserRole.ADMIN, UserRole.resolve("ADMIN", true, "manager"));
        assertEquals(UserRole.USER, UserRole.resolve("USER", false, "user1"));
        assertEquals(UserRole.SUPER_ADMIN, UserRole.resolve(null, true, "admin"));
        assertEquals(UserRole.ADMIN, UserRole.resolve(null, true, "judge"));
        assertEquals(UserRole.USER, UserRole.resolve(null, false, "user1"));
    }
}
