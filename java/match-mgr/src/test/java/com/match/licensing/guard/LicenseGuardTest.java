package com.match.licensing.guard;

import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.service.LicenseStatusService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LicenseGuardTest {
    @Test
    public void rejectsExpiredLicense() {
        LicenseStatusService statusService = mock(LicenseStatusService.class);
        when(statusService.currentStatus()).thenReturn(
                new LicenseStatus(LicenseState.EXPIRED, "license-1", "Fixture", null, 4));
        LicenseGuard guard = new LicenseGuard(statusService);

        try {
            guard.requireActive();
            fail("expired license must be rejected");
        } catch (LicenseAccessException expected) {
            assertEquals("EXPIRED", expected.getReasonCode());
        }
    }

    @Test
    public void enforcesProcessingServerCapacity() {
        LicenseStatusService statusService = mock(LicenseStatusService.class);
        when(statusService.currentStatus()).thenReturn(
                new LicenseStatus(LicenseState.ACTIVE, "license-1", "Fixture", null, 4));
        LicenseGuard guard = new LicenseGuard(statusService);

        guard.requireProcessingServerCapacity(4);
        try {
            guard.requireProcessingServerCapacity(5);
            fail("server limit must be enforced");
        } catch (LicenseAccessException expected) {
            assertEquals("SERVER_LIMIT_EXCEEDED", expected.getReasonCode());
        }
    }
}
