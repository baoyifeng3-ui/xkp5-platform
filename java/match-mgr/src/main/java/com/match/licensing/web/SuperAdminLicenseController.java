package com.match.licensing.web;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.config.LicenseProperties;
import com.match.licensing.identity.HostIdentity;
import com.match.licensing.identity.HostIdentityProvider;
import com.match.licensing.persistence.LicenseAuditMapper;
import com.match.licensing.persistence.LicenseAuditRecord;
import com.match.licensing.persistence.PlatformInstallation;
import com.match.licensing.persistence.PlatformLicenseMapper;
import com.match.licensing.persistence.PlatformLicenseRecord;
import com.match.licensing.service.InstallationService;
import com.match.licensing.service.LicenseStatusService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/super-admin/license")
public class SuperAdminLicenseController {
    private final RoleGuard roleGuard;
    private final LicenseStatusService statusService;
    private final InstallationService installationService;
    private final LicenseAuditMapper auditMapper;
    private final HostIdentityProvider identityProvider;
    private final LicenseProperties licenseProperties;
    private final PlatformLicenseMapper licenseMapper;

    public SuperAdminLicenseController(RoleGuard roleGuard, LicenseStatusService statusService,
                                       InstallationService installationService, LicenseAuditMapper auditMapper,
                                       HostIdentityProvider identityProvider, LicenseProperties licenseProperties,
                                       PlatformLicenseMapper licenseMapper) {
        this.roleGuard = roleGuard;
        this.statusService = statusService;
        this.installationService = installationService;
        this.auditMapper = auditMapper;
        this.identityProvider = identityProvider;
        this.licenseProperties = licenseProperties;
        this.licenseMapper = licenseMapper;
    }

    @GetMapping("/diagnostics")
    public ResponseResult<Object> diagnostics() {
        roleGuard.requireSuperAdmin();
        LicenseStatus status = statusService.currentStatus();
        PlatformLicenseRecord license = licenseMapper.selectActive();
        PlatformInstallation installation = installationService.installation();
        HostIdentity identity = identityProvider.load();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("state", status == null ? null : status.getState());
        diagnostics.put("organization", license == null ? null : license.getOrganization());
        diagnostics.put("expiresAt", license == null ? null : license.getExpiresAt());
        diagnostics.put("importedAt", license == null ? null : license.getImportedAt());
        return Response.makeOKRsp(diagnostics);
    }

    @GetMapping("/audits")
    public ResponseResult<Object> audits(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        roleGuard.requireSuperAdmin();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Page<LicenseAuditRecord> result = new Page<>(safePage, safeSize);
        auditMapper.selectPage(result, new QueryWrapper<LicenseAuditRecord>().orderByDesc("created_at"));
        return Response.makeOKRsp(result);
    }

    @PostMapping("/revalidate")
    public ResponseResult<Object> revalidate() {
        roleGuard.requireSuperAdmin();
        return Response.makeOKRsp(statusService.currentStatus());
    }
}
