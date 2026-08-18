package com.match.licensing.web;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.persistence.LicenseAuditMapper;
import com.match.licensing.persistence.LicenseAuditRecord;
import com.match.licensing.persistence.PlatformInstallation;
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

    public SuperAdminLicenseController(RoleGuard roleGuard, LicenseStatusService statusService,
                                       InstallationService installationService, LicenseAuditMapper auditMapper) {
        this.roleGuard = roleGuard;
        this.statusService = statusService;
        this.installationService = installationService;
        this.auditMapper = auditMapper;
    }

    @GetMapping("/diagnostics")
    public ResponseResult<Object> diagnostics() {
        roleGuard.requireSuperAdmin();
        LicenseStatus status = statusService.currentStatus();
        PlatformInstallation installation = installationService.installation();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("state", status == null ? null : status.getState());
        diagnostics.put("licenseId", status == null ? null : status.getLicenseId());
        diagnostics.put("installationId", installation == null ? null : installation.getInstallationId());
        diagnostics.put("maxTrustedTime", installation == null ? null : installation.getMaxTrustedTime());
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
