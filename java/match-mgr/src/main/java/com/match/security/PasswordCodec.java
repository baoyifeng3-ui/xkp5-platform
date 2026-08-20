package com.match.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordCodec {
    private static final String PREFIX = "{bcrypt}";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return PREFIX + encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isEncoded(storedPassword)) {
            return encoder.matches(rawPassword, storedPassword.substring(PREFIX.length()));
        }
        return rawPassword.equals(storedPassword);
    }

    public boolean isEncoded(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(PREFIX);
    }
}
