package com.match.licensing.web;

import com.match.entity.User;
import com.match.licensing.model.PlatformRequest;
import com.match.licensing.persistence.PlatformLicenseRecord;
import com.match.licensing.service.LicenseImportService;
import com.match.licensing.service.LicenseRequestService;
import com.match.licensing.service.LicenseStatusService;
import com.match.security.RoleGuard;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminLicenseControllerTest {
    @Test
    public void normalAdminCanExportAndImport() throws Exception {
        LicenseRequestService requests = mock(LicenseRequestService.class);
        LicenseImportService imports = mock(LicenseImportService.class);
        LicenseStatusService status = mock(LicenseStatusService.class);
        RoleGuard roleGuard = mock(RoleGuard.class);
        User admin = new User();
        admin.setUserId(7);
        when(roleGuard.requireBusinessAdmin()).thenReturn(admin);
        PlatformRequest request = new PlatformRequest(1, "request-1", "installation-1", "challenge",
                "sha256:fingerprint", "DEVELOPMENT", "Fixture Lab", "5.0.0",
                "2026-08-18T00:00:00Z");
        when(requests.create("Fixture Lab", 7)).thenReturn(request);
        when(requests.filename(request)).thenReturn("xkp-platform-request-1.xkpreq");
        when(imports.importLicense(any(byte[].class), eq(7))).thenReturn(new PlatformLicenseRecord());
        AdminLicenseController controller = new AdminLicenseController(requests, imports, status, roleGuard);

        controller.createRequest(new LicenseRequestInput("Fixture Lab"));
        controller.importLicense(new MockMultipartFile("file", "license.xkplic",
                "application/json", "{}".getBytes()));

        verify(roleGuard, times(2)).requireBusinessAdmin();
        verify(requests).create("Fixture Lab", 7);
        verify(imports).importLicense(any(byte[].class), eq(7));
    }

    @Test
    public void downloadRequiresBusinessAdminAndConsumesPendingFile() {
        LicenseRequestService requests = mock(LicenseRequestService.class);
        LicenseImportService imports = mock(LicenseImportService.class);
        LicenseStatusService status = mock(LicenseStatusService.class);
        RoleGuard roleGuard = mock(RoleGuard.class);
        User admin = new User();
        admin.setUserId(7);
        when(roleGuard.requireBusinessAdmin()).thenReturn(admin);
        when(requests.takeDownload("request-1")).thenReturn("{}".getBytes());
        when(requests.filenameFor("request-1")).thenReturn("xkp-platform-request-1.xkpreq");
        AdminLicenseController controller = new AdminLicenseController(requests, imports, status, roleGuard);

        controller.download("request-1");

        verify(roleGuard).requireBusinessAdmin();
        verify(requests).takeDownload("request-1");
    }
}
