package com.match.controller;

import com.match.dto.GradingScoresRequest;
import com.match.dto.ReturnSubmissionRequest;
import com.match.security.AdminGuard;
import com.match.service.impl.PaperGradingService;
import com.match.service.impl.PaperExportService;
import com.match.service.impl.SystemSettingService;
import com.match.entity.User;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin/grading")
public class AdminGradingController {
    private final PaperGradingService gradingService;
    private final AdminGuard adminGuard;
    private final PaperExportService exportService;
    private final SystemSettingService systemSettingService;

    public AdminGradingController(PaperGradingService gradingService, AdminGuard adminGuard,
                                  PaperExportService exportService, SystemSettingService systemSettingService) {
        this.gradingService = gradingService;
        this.adminGuard = adminGuard;
        this.exportService = exportService;
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("submissions")
    public ResponseResult<Object> list(@RequestParam String paperType,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(gradingService.list(requireActivePaper(paperType), status, keyword));
    }

    @GetMapping("submissions/{submissionId}")
    public ResponseResult<Object> detail(@PathVariable Integer submissionId) {
        adminGuard.requireAdmin();
        requireActiveSubmission(submissionId);
        return Response.makeOKRsp(gradingService.detail(submissionId));
    }

    @PutMapping("submissions/{submissionId}/scores")
    public ResponseResult<Object> saveScores(@PathVariable Integer submissionId,
                                              @RequestBody GradingScoresRequest request) {
        adminGuard.requireAdmin();
        requireActiveSubmission(submissionId);
        return Response.makeOKRsp(gradingService.saveScores(submissionId, request));
    }

    @PostMapping("submissions/{submissionId}/complete")
    public ResponseResult<Object> complete(@PathVariable Integer submissionId) {
        adminGuard.requireAdmin();
        requireActiveSubmission(submissionId);
        return Response.makeOKRsp(gradingService.complete(submissionId));
    }

    @PostMapping("submissions/{submissionId}/return")
    public ResponseResult<Object> returnForRevision(@PathVariable Integer submissionId,
                                                     @RequestBody ReturnSubmissionRequest request) {
        adminGuard.requireAdmin();
        requireActiveSubmission(submissionId);
        return Response.makeOKRsp(gradingService.returnForRevision(
                submissionId, request == null ? null : request.getReason()));
    }

    @GetMapping("exports")
    public ResponseResult<Object> exports(@RequestParam String paperType) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(exportService.list(requireActivePaper(paperType)));
    }

    @GetMapping("exports/status")
    public ResponseResult<Object> exportStatus(@RequestParam String paperType) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(exportService.currentExportStatus(requireActivePaper(paperType)));
    }

    @PostMapping("exports")
    public void export(@RequestParam String paperType, HttpServletResponse response) throws IOException {
        User admin = adminGuard.requireAdmin();
        writeArtifact(response, exportService.create(admin, requireActivePaper(paperType)));
    }

    @GetMapping("exports/{batchId}/download")
    public void download(@PathVariable Integer batchId, HttpServletResponse response) throws IOException {
        adminGuard.requireAdmin();
        PaperExportService.ExportArtifact artifact = exportService.requireDownloadable(batchId,
                requireActivePaper(exportService.paperType(batchId)));
        writeArtifact(response, artifact);
    }

    private void writeArtifact(HttpServletResponse response, PaperExportService.ExportArtifact artifact)
            throws IOException {
        String encoded = URLEncoder.encode(artifact.getFileName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("X-Export-Key", artifact.getBatch().getExportKey());
        response.setHeader("X-Export-Batch-Id", String.valueOf(artifact.getBatch().getExportBatchId()));
        response.setContentLength(artifact.getBytes().length);
        response.getOutputStream().write(artifact.getBytes());
    }

    private String requireActivePaper(String requestedPaper) {
        String activePaper = systemSettingService.getActivePaper();
        if (activePaper.isEmpty()) {
            throw new IllegalArgumentException("当前尚未启用试卷");
        }
        if (requestedPaper == null || !activePaper.equalsIgnoreCase(requestedPaper.trim())) {
            throw new IllegalArgumentException("判分和导出仅支持当前启用的 " + activePaper + " 卷");
        }
        return activePaper;
    }

    private void requireActiveSubmission(Integer submissionId) {
        requireActivePaper(gradingService.requireSubmission(submissionId).getPaperType());
    }
}
