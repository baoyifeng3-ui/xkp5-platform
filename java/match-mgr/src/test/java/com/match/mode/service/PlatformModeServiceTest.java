package com.match.mode.service;

import com.match.Application;
import com.match.agent.service.AgentAuditService;
import com.match.entity.User;
import com.match.licensing.guard.LicenseAccessException;
import com.match.licensing.guard.LicenseGuard;
import com.match.mode.model.PlatformModeView;
import com.match.mode.persistence.PlatformModeMapper;
import com.match.mode.persistence.PlatformModeRecord;
import com.match.security.AdminAccessException;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class PlatformModeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T04:05:06Z");

    private PlatformModeMapper mapper;
    private RoleGuard roleGuard;
    private LicenseGuard licenseGuard;
    private AgentAuditService auditService;
    private ApplicationEventPublisher events;
    private PlatformModeService service;

    @Before
    public void setUp() {
        mapper = mock(PlatformModeMapper.class);
        roleGuard = mock(RoleGuard.class);
        licenseGuard = mock(LicenseGuard.class);
        auditService = mock(AgentAuditService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PlatformModeService(mapper, roleGuard, licenseGuard, auditService,
                events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void currentReturnsPersistedSingletonAsImmutableView() throws Exception {
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 19, 3, 2, 1);
        when(mapper.selectCurrent()).thenReturn(record("TRAINING", 7L, 12, changedAt));

        PlatformModeView view = service.current();

        assertEquals("TRAINING", view.getMode());
        assertEquals(7L, view.getGeneration());
        assertEquals(Integer.valueOf(12), view.getChangedBy());
        assertEquals(changedAt, view.getChangedAt());
        assertEquals(0, PlatformModeView.class.getDeclaredMethods().length - 4);
        Transactional transactional = PlatformModeService.class.getMethod("current")
                .getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    @Test
    public void changeIncrementsOnceAuditsAndPublishesExactEvent() {
        User actor = actor(41, true);
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        when(mapper.selectForUpdate()).thenReturn(record("TRAINING", 7L, 12,
                LocalDateTime.of(2026, 8, 19, 3, 2, 1)));
        when(mapper.compareAndSet("TRAINING", 7L, "COMPETITION", 8L, 41,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(1);

        PlatformModeView changed = service.change("COMPETITION", actor);

        assertEquals("COMPETITION", changed.getMode());
        assertEquals(8L, changed.getGeneration());
        assertEquals(Integer.valueOf(41), changed.getChangedBy());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), changed.getChangedAt());
        verify(licenseGuard).requireActive();
        verify(mapper).compareAndSet("TRAINING", 7L, "COMPETITION", 8L, 41,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(auditService).recordSuccess("PLATFORM_MODE_TO_COMPETITION", 41, null, null);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        PlatformModeChangedEvent event = (PlatformModeChangedEvent) published.getValue();
        assertEquals("TRAINING", event.getSourceMode());
        assertEquals("COMPETITION", event.getTargetMode());
        assertEquals(8L, event.getGeneration());
        assertEquals(41, event.getActorUserId());
        assertEquals("ADMIN", event.getActorRole());

        InOrder order = inOrder(mapper, licenseGuard, auditService, events);
        order.verify(mapper).selectForUpdate();
        order.verify(licenseGuard).requireActive();
        order.verify(mapper).compareAndSet(anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), any(LocalDateTime.class));
        order.verify(auditService).recordSuccess(anyString(), anyInt(), any(), any());
        order.verify(events).publishEvent(any(Object.class));
    }

    @Test
    public void sameTargetIsIdempotentWithoutSideEffects() {
        User actor = actor(41, true);
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 19, 3, 2, 1);
        when(mapper.selectForUpdate()).thenReturn(record("TRAINING", 7L, 12, changedAt));

        PlatformModeView unchanged = service.change("TRAINING", actor);

        assertEquals("TRAINING", unchanged.getMode());
        assertEquals(7L, unchanged.getGeneration());
        assertEquals(Integer.valueOf(12), unchanged.getChangedBy());
        assertEquals(changedAt, unchanged.getChangedAt());
        verifyZeroInteractions(licenseGuard, auditService, events);
        verify(mapper, never()).compareAndSet(anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), any(LocalDateTime.class));
    }

    @Test
    public void staleCompareAndSetFailsWithoutAuditOrEvent() {
        User actor = adminActor();
        when(mapper.selectForUpdate()).thenReturn(record("TRAINING", 7L, 12, LocalDateTime.now()));
        when(mapper.compareAndSet(anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), any(LocalDateTime.class))).thenReturn(0);

        expect(ModeConflictException.class, () -> service.change("COMPETITION", actor));

        verify(licenseGuard).requireActive();
        verifyZeroInteractions(auditService, events);
    }

    @Test
    public void missingOrInvalidSingletonFailsClosed() {
        User actor = adminActor();
        when(mapper.selectCurrent()).thenReturn(null);
        expect(IllegalStateException.class, () -> service.current());

        when(mapper.selectForUpdate()).thenReturn(null);
        expect(IllegalStateException.class, () -> service.change("COMPETITION", actor));
        verifyZeroInteractions(licenseGuard);

        when(mapper.selectCurrent()).thenReturn(record("competition", 1L, 0, LocalDateTime.now()));
        expect(IllegalStateException.class, () -> service.current());
        when(mapper.selectCurrent()).thenReturn(record("TRAINING", 0L, 0, LocalDateTime.now()));
        expect(IllegalStateException.class, () -> service.current());
        when(mapper.selectCurrent()).thenReturn(record("TRAINING", null, 0, LocalDateTime.now()));
        expect(IllegalStateException.class, () -> service.current());
    }

    @Test
    public void rejectsEveryNonExactTargetBeforeLocking() {
        User actor = adminActor();
        String[] invalid = {null, "", " ", " training", "TRAINING ", "training",
                "competition", "OTHER"};

        for (String target : invalid) {
            expect(IllegalArgumentException.class, () -> service.change(target, actor));
        }

        verify(mapper, never()).selectForUpdate();
        verifyZeroInteractions(licenseGuard, auditService, events);
    }

    @Test
    public void rejectsNullDisabledUserAndNonBusinessAdminRoles() {
        expect(AdminAccessException.class, () -> service.change("COMPETITION", null));

        User disabled = actor(41, false);
        expect(AdminAccessException.class, () -> service.change("COMPETITION", disabled));

        User user = actor(42, true);
        when(roleGuard.roleOf(user)).thenReturn(UserRole.USER);
        expect(AdminAccessException.class, () -> service.change("COMPETITION", user));

        User superAdmin = actor(43, true);
        when(roleGuard.roleOf(superAdmin)).thenReturn(UserRole.SUPER_ADMIN);
        expect(AdminAccessException.class, () -> service.change("COMPETITION", superAdmin));

        verify(mapper, never()).selectForUpdate();
        verifyZeroInteractions(licenseGuard, auditService, events);
    }

    @Test
    public void inactiveLicensePreventsMutationAuditAndEvent() {
        User actor = adminActor();
        when(mapper.selectForUpdate()).thenReturn(record("TRAINING", 7L, 12, LocalDateTime.now()));
        doThrow(new LicenseAccessException("EXPIRED", "expired"))
                .when(licenseGuard).requireActive();

        expect(LicenseAccessException.class, () -> service.change("COMPETITION", actor));

        verify(mapper, never()).compareAndSet(anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), any(LocalDateTime.class));
        verifyZeroInteractions(auditService, events);
    }

    @Test
    public void generationOverflowFailsBeforeMutationAuditAndEvent() {
        User actor = adminActor();
        when(mapper.selectForUpdate()).thenReturn(record("TRAINING", Long.MAX_VALUE, 12,
                LocalDateTime.now()));

        expect(ArithmeticException.class, () -> service.change("COMPETITION", actor));

        verify(licenseGuard).requireActive();
        verify(mapper, never()).compareAndSet(anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), any(LocalDateTime.class));
        verifyZeroInteractions(auditService, events);
    }

    @Test
    public void mapperLocksSingletonAndUsesExactCompareAndSetSql() throws Exception {
        Method current = PlatformModeMapper.class.getMethod("selectCurrent");
        String currentSql = sql(current.getAnnotation(Select.class).value());
        assertTrue(currentSql.contains("FROM platform_mode"));
        assertTrue(currentSql.contains("singleton_id = 1"));

        Method locked = PlatformModeMapper.class.getMethod("selectForUpdate");
        String lockSql = sql(locked.getAnnotation(Select.class).value());
        assertTrue(lockSql.contains("singleton_id = 1"));
        assertTrue(lockSql.endsWith("FOR UPDATE"));

        Method cas = PlatformModeMapper.class.getMethod("compareAndSet", String.class, long.class,
                String.class, long.class, int.class, LocalDateTime.class);
        String casSql = sql(cas.getAnnotation(Update.class).value());
        assertTrue(casSql.contains("SET mode = #{targetMode}, generation = #{nextGeneration}"));
        assertTrue(casSql.contains("changed_by = #{actorUserId}, changed_at = #{changedAt}"));
        assertTrue(casSql.contains("singleton_id = 1"));
        assertTrue(casSql.contains("mode = #{expectedMode}"));
        assertTrue(casSql.contains("generation = #{expectedGeneration}"));
    }

    @Test
    public void changeIsTransactional() throws Exception {
        Transactional transactional = PlatformModeService.class
                .getMethod("change", String.class, User.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional);
    }

    @Test
    public void applicationScansPlatformModeMapperPackage() {
        MapperScan scan = Application.class.getAnnotation(MapperScan.class);

        assertNotNull(scan);
        assertTrue(Arrays.asList(scan.value()).contains("com.match.mode.persistence"));
    }

    private User adminActor() {
        User actor = actor(41, true);
        when(roleGuard.roleOf(actor)).thenReturn(UserRole.ADMIN);
        return actor;
    }

    private User actor(int id, boolean enabled) {
        User actor = new User();
        actor.setUserId(id);
        actor.setEnabled(enabled);
        return actor;
    }

    private PlatformModeRecord record(String mode, Long generation, int changedBy,
                                      LocalDateTime changedAt) {
        PlatformModeRecord record = new PlatformModeRecord();
        record.setSingletonId(1);
        record.setMode(mode);
        record.setGeneration(generation);
        record.setChangedBy(changedBy);
        record.setChangedAt(changedAt);
        return record;
    }

    private String sql(String[] fragments) {
        assertNotNull(fragments);
        return String.join("", fragments).replaceAll("\\s+", " ").trim();
    }

    private void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            fail("expected " + type.getSimpleName());
        } catch (Throwable thrown) {
            assertTrue("expected " + type.getSimpleName() + " but was " + thrown,
                    type.isInstance(thrown));
        }
    }
}
