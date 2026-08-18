package com.match.security;

public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    USER;

    public static UserRole resolve(String storedRole, Boolean legacyAdmin, String userName) {
        if (storedRole != null && !storedRole.trim().isEmpty()) {
            return UserRole.valueOf(storedRole.trim().toUpperCase());
        }
        if (Boolean.TRUE.equals(legacyAdmin)) {
            return "admin".equalsIgnoreCase(userName) ? SUPER_ADMIN : ADMIN;
        }
        return USER;
    }
}
