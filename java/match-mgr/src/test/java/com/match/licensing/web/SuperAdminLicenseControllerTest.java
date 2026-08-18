package com.match.licensing.web;

import com.match.licensing.persistence.LicenseAuditMapper;
import com.match.licensing.service.InstallationService;
import com.match.licensing.service.LicenseStatusService;
import com.match.security.RoleGuard;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SuperAdminLicenseControllerTest {
    @Test
    public void diagnosticsAndAuditsRequireSuperAdmin() {
        RoleGuard roleGuard = mock(RoleGuard.class);
        LicenseStatusService status = mock(LicenseStatusService.class);
        InstallationService installation = mock(InstallationService.class);
        LicenseAuditMapper audits = mock(LicenseAuditMapper.class);
        SuperAdminLicenseController controller = new SuperAdminLicenseController(
                roleGuard, status, installation, audits);

        controller.diagnostics();
        controller.audits(1, 50);

        verify(roleGuard, org.mockito.Mockito.times(2)).requireSuperAdmin();
    }
}
