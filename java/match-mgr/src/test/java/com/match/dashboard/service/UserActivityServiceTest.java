package com.match.dashboard.service;

import com.match.dashboard.persistence.UserSessionActivityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class UserActivityServiceTest {
    private final UserSessionActivityMapper mapper = mock(UserSessionActivityMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC);
    private final UserActivityService service = new UserActivityService(mapper, clock);

    @Test
    public void productionConstructorIsExplicitlyAutowiredForSpring() throws Exception {
        assertEquals(true, UserActivityService.class
                .getConstructor(UserSessionActivityMapper.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test
    public void loginStoresDigestAndFiveMinuteExpiry() {
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> loginAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> activityAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);

        service.recordLogin(17, "secret-session-token");

        verify(mapper).upsertActivity(digest.capture(), anyInt(), loginAt.capture(), activityAt.capture(), expiresAt.capture());
        assertNotEquals("secret-session-token", digest.getValue());
        assertEquals(64, digest.getValue().length());
        assertEquals(LocalDateTime.of(2026, 8, 19, 10, 15, 30), activityAt.getValue());
        assertEquals(activityAt.getValue().plusMinutes(5), expiresAt.getValue());
    }

    @Test
    public void logoutDeletesOnlyDigestOfCurrentSession() {
        service.logout("session-a");
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(mapper).deleteSession(digest.capture());
        assertNotEquals("session-a", digest.getValue());
        assertEquals(64, digest.getValue().length());
    }

    @Test
    public void distinctSessionsUseDistinctDigests() {
        service.touch(17, "session-a");
        service.touch(17, "session-b");

        ArgumentCaptor<String> digests = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(2)).upsertActivity(digests.capture(), anyInt(), any(), any(), any());
        assertEquals(2, digests.getAllValues().stream().distinct().count());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTokenIsRejected() {
        service.touch(17, "  ");
    }

    @Test
    public void onlineCountUsesFiveMinuteCutoff() {
        service.onlineUserCount();
        verify(mapper).deleteExpired(LocalDateTime.of(2026, 8, 19, 10, 15, 30), 500);
        verify(mapper).countDistinctActiveUsers(LocalDateTime.of(2026, 8, 19, 10, 10, 30));
    }

    @Test
    public void activityExpiryNeverOutlivesAuthenticationToken() {
        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);

        service.touch(17, "short-session", 30);

        verify(mapper).upsertActivity(any(), anyInt(), any(), any(), expiresAt.capture());
        assertEquals(LocalDateTime.of(2026, 8, 19, 10, 16, 0), expiresAt.getValue());
    }
}
