package com.match.dashboard.service;

import com.match.dashboard.persistence.UserSessionActivityMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class UserActivityService {
    private static final long ACTIVE_MINUTES = 5;
    private static final long ACTIVE_SECONDS = ACTIVE_MINUTES * 60;
    private static final int CLEANUP_LIMIT = 500;
    private final UserSessionActivityMapper mapper;
    private final Clock clock;

    public UserActivityService(UserSessionActivityMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    UserActivityService(UserSessionActivityMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public void recordLogin(int userId, String token) { touchAt(userId, token, -1); }
    public void recordLogin(int userId, String token, long tokenTimeoutSeconds) { touchAt(userId, token, tokenTimeoutSeconds); }
    public void touch(int userId, String token) { touchAt(userId, token, -1); }
    public void touch(int userId, String token, long tokenTimeoutSeconds) { touchAt(userId, token, tokenTimeoutSeconds); }
    public void logout(String token) { mapper.deleteSession(digest(requireToken(token))); }
    public int onlineUserCount() {
        LocalDateTime now = now();
        mapper.deleteExpired(now, CLEANUP_LIMIT);
        return mapper.countDistinctActiveUsers(now.minusMinutes(ACTIVE_MINUTES));
    }

    private void touchAt(int userId, String token, long tokenTimeoutSeconds) {
        LocalDateTime now = now();
        long effectiveSeconds = tokenTimeoutSeconds < 0
                ? ACTIVE_SECONDS : Math.min(ACTIVE_SECONDS, Math.max(0, tokenTimeoutSeconds));
        mapper.upsertActivity(digest(requireToken(token)), userId, now, now, now.plusSeconds(effectiveSeconds));
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private String digest(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String requireToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        return token;
    }
}
