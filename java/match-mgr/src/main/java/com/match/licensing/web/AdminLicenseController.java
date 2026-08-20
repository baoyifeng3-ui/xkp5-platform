package com.match.licensing.web;

import com.match.entity.User;
import com.match.licensing.model.PlatformRequest;
import com.match.licensing.persistence.PlatformLicenseRecord;
import com.match.licensing.service.LicenseImportService;
import com.match.licensing.service.LicenseRequestService;
import com.match.licensing.service.LicenseStatusService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/license")
public class AdminLicenseController {
    private final LicenseRequestService requestService;
    private final LicenseImportService importService;
    private final LicenseStatusService statusService;
    private final RoleGuard roleGuard;

    public AdminLicenseController(LicenseRequestService requestService, LicenseImportService importService,
                                  LicenseStatusService statusService, RoleGuard roleGuard) {
        this.requestService = requestService;
        this.importService = importService;
        this.statusService = statusService;
        this.roleGuard = roleGuard;
    }

    @GetMapping("/status")
    public ResponseResult<Object> status() {
        roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(statusService.currentStatus());
    }

    @PostMapping("/requests")
    public ResponseResult<Object> createRequest(@RequestBody(required = false) LicenseRequestInput input) {
        User actor = roleGuard.requireBusinessAdmin();
        PlatformRequest request = requestService.create(input == null ? null : input.getOrganization(), actor.getUserId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", request.getRequestId());
        result.put("filename", requestService.filename(request));
        return Response.makeOKRsp(result);
    }

    @GetMapping("/requests/{requestId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String requestId) {
        roleGuard.requireBusinessAdmin();
        byte[] content = requestService.takeDownload(requestId);
        String disposition = "attachment; filename=\"" + requestService.filenameFor(requestId) + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(content);
    }

    @PostMapping("/import")
    public ResponseResult<Object> importLicense(@RequestParam("file") MultipartFile file) {
        User actor = roleGuard.requireBusinessAdmin();
        try {
            PlatformLicenseRecord imported = importService.importLicense(file.getBytes(), actor.getUserId());
            return Response.makeOKRsp(imported.getLicenseId());
        } catch (IOException exception) {
            throw new LicenseImportException("FILE_READ_FAILED", "无法读取授权文件", exception);
        }
    }
}
