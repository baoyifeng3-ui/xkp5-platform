package com.match.mode.service;

import com.match.agent.service.AgentAuditService;
import com.match.entity.User;
import com.match.licensing.guard.LicenseGuard;
import com.match.mode.model.PlatformModeView;
import com.match.mode.persistence.PlatformModeMapper;
import com.match.mode.persistence.PlatformModeRecord;
import com.match.security.AdminAccessException;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PlatformModeService {
    public static final String TRAINING = "TRAINING";
    public static final String COMPETITION = "COMPETITION";

    private final PlatformModeMapper mapper;
    private final RoleGuard roleGuard;
    private final LicenseGuard licenseGuard;
    private final AgentAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlatformModeService(PlatformModeMapper mapper, RoleGuard roleGuard,
                               LicenseGuard licenseGuard, AgentAuditService auditService,
                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.mapper = mapper;
        this.roleGuard = roleGuard;
        this.licenseGuard = licenseGuard;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlatformModeView current() {
        return view(requireValid(mapper.selectCurrent()));
    }

    @Transactional
    public PlatformModeView change(String target, User actor) {
        int actorUserId = requireBusinessAdmin(actor);
        requireTarget(target);

        PlatformModeRecord current = requireValid(mapper.selectForUpdate());
        if (target.equals(current.getMode())) {
            return view(current);
        }

        licenseGuard.requireActive();
        long nextGeneration = Math.addExact(current.getGeneration(), 1L);
        LocalDateTime changedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = mapper.compareAndSet(current.getMode(), current.getGeneration(), target,
                nextGeneration, actorUserId, changedAt);
        if (updated != 1) {
            throw new ModeConflictException("Platform mode changed concurrently");
        }

        auditService.recordSuccess("PLATFORM_MODE_TO_" + target, actorUserId, null, null);
        eventPublisher.publishEvent(new PlatformModeChangedEvent(current.getMode(), target,
                nextGeneration, actorUserId, UserRole.ADMIN.name()));
        return new PlatformModeView(target, nextGeneration, actorUserId, changedAt);
    }

    private int requireBusinessAdmin(User actor) {
        if (actor == null || !Boolean.TRUE.equals(actor.getEnabled())
                || actor.getUserId() == null || roleGuard.roleOf(actor) != UserRole.ADMIN) {
            throw new AdminAccessException("仅普通管理员可以执行此操作");
        }
        return actor.getUserId();
    }

    private void requireTarget(String target) {
        if (!TRAINING.equals(target) && !COMPETITION.equals(target)) {
            throw new IllegalArgumentException("Platform mode target is invalid");
        }
    }

    private PlatformModeRecord requireValid(PlatformModeRecord record) {
        if (record == null || !Integer.valueOf(1).equals(record.getSingletonId())
                || (!TRAINING.equals(record.getMode()) && !COMPETITION.equals(record.getMode()))
                || record.getGeneration() == null || record.getGeneration() <= 0
                || record.getChangedBy() == null || record.getChangedAt() == null) {
            throw new IllegalStateException("Platform mode singleton is missing or invalid");
        }
        return record;
    }

    private PlatformModeView view(PlatformModeRecord record) {
        return new PlatformModeView(record.getMode(), record.getGeneration(),
                record.getChangedBy(), record.getChangedAt());
    }
}
