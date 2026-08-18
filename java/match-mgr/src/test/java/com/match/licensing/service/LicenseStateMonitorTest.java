package com.match.licensing.service;

import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseStateMonitorTest {
    @Test
    public void publishesAndAuditsOnlyActualStateTransitions() {
        LicenseStatusService statusService = mock(LicenseStatusService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        LicenseAuditService audit = mock(LicenseAuditService.class);
        when(statusService.currentStatus()).thenReturn(
                new LicenseStatus(LicenseState.ACTIVE, "license-1", "Fixture", null, 4),
                new LicenseStatus(LicenseState.EXPIRED, "license-1", "Fixture", null, 4),
                new LicenseStatus(LicenseState.EXPIRED, "license-1", "Fixture", null, 4));
        LicenseStateMonitor monitor = new LicenseStateMonitor(statusService, events, audit);

        monitor.tick();
        verify(events, never()).publishEvent(any());
        monitor.tick();
        monitor.tick();

        verify(events, times(1)).publishEvent(any(LicenseStateChangedEvent.class));
        verify(audit, times(1)).recordSuccess("LICENSE_STATE_CHANGED", null, null, "license-1");
    }
}
