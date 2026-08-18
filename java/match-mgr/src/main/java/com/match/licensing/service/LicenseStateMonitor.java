package com.match.licensing.service;

import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LicenseStateMonitor {
    private final LicenseStatusService statusService;
    private final ApplicationEventPublisher events;
    private final LicenseAuditService auditService;
    private LicenseState previousState;

    public LicenseStateMonitor(LicenseStatusService statusService, ApplicationEventPublisher events,
                               LicenseAuditService auditService) {
        this.statusService = statusService;
        this.events = events;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${xkp.licensing.monitor-delay-ms:60000}")
    public synchronized void tick() {
        LicenseStatus status = statusService.currentStatus();
        LicenseState current = status.getState();
        if ((previousState == null && !status.isUsable())
                || (previousState != null && previousState != current)) {
            events.publishEvent(new LicenseStateChangedEvent(previousState, current, status.getLicenseId()));
            auditService.recordSuccess("LICENSE_STATE_CHANGED", null, null, status.getLicenseId());
        }
        previousState = current;
    }
}
