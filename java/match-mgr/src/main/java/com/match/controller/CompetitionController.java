package com.match.controller;

import com.match.security.AdminGuard;
import com.match.service.impl.PaperCatalogService;
import com.match.service.impl.SystemSettingService;
import com.match.service.impl.PaperResourceService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import cn.dev33.satoken.stp.StpUtil;
import com.match.dto.PaperSelectionRequest;
import com.match.dto.PaperCreateRequest;
import com.match.dto.PlatformNameRequest;
import com.match.dto.PlatformSettingsRequest;
import com.match.dto.CompetitionContentRequest;
import com.match.dto.CompetitionHelpRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Locale;
import com.match.util.dfs.FastDFSClient;
import org.apache.commons.io.FilenameUtils;

@RestController
@RequestMapping("/competition")
public class CompetitionController {
    private final SystemSettingService settingService;
    private final AdminGuard adminGuard;
    private final PaperResourceService paperResourceService;
    private final PaperCatalogService paperCatalogService;

    public CompetitionController(SystemSettingService settingService, AdminGuard adminGuard,
                                 PaperResourceService paperResourceService,
                                 PaperCatalogService paperCatalogService) {
        this.settingService = settingService;
        this.adminGuard = adminGuard;
        this.paperResourceService = paperResourceService;
        this.paperCatalogService = paperCatalogService;
    }

    @GetMapping
    public ResponseResult<Object> get() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activePaper", settingService.getActivePaper());
        data.putAll(settingService.getPlatformSettings());
        data.put("competitionContent", settingService.getCompetitionContent());
        data.put("competitionHelpContent", settingService.getCompetitionHelpContent());
        List<String> availablePapers = paperCatalogService.list();
        data.put("availablePapers", availablePapers);
        Map<String, Object> resources = new LinkedHashMap<>();
        for (String paper : availablePapers) {
            resources.put(paper, paperResourceService.status(paper));
        }
        data.put("paperResources", resources);
        return Response.makeOKRsp(data);
    }

    @PostMapping("/papers")
    public ResponseResult<Object> createPaper(@RequestBody PaperCreateRequest request) {
        adminGuard.requireAdmin();
        String paper = paperCatalogService.create(
                request == null ? null : request.getPaperName(), StpUtil.getLoginIdAsInt());
        return Response.makeOKRsp(paperResourceService.status(paper));
    }

    @PostMapping
    public ResponseResult<Object> set(@RequestBody PaperSelectionRequest request) {
        adminGuard.requireAdmin();
        if (request.getPaperType() != null && !request.getPaperType().trim().isEmpty()) {
            paperResourceService.requireSelectable(request.getPaperType());
        }
        String activePaper = settingService.setActivePaper(request.getPaperType(), StpUtil.getLoginIdAsInt());
        return Response.makeOKRsp(Collections.singletonMap("activePaper", activePaper));
    }

    @PutMapping("/platform-name")
    public ResponseResult<Object> setPlatformName(@RequestBody PlatformNameRequest request) {
        adminGuard.requireAdmin();
        String platformName = settingService.setPlatformName(
                request.getPlatformName(), StpUtil.getLoginIdAsInt());
        return Response.makeOKRsp(Collections.singletonMap("platformName", platformName));
    }

    @PutMapping("/settings")
    public ResponseResult<Object> setPlatformSettings(@RequestBody PlatformSettingsRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(settingService.setPlatformSettings(
                request.getPlatformName(), request.getThemeColor(), request.getLoginBackgroundUrl(),
                request.getPlatformLogoUrl(), request.getLoginEnglishSubtitle(),
                request.getLoginBrandName(), request.getLoginTitle(), request.getLoginDescription(),
                request.getLoginCopyright(),
                StpUtil.getLoginIdAsInt()));
    }

    @PostMapping("/settings/login-background")
    public ResponseResult<Object> uploadLoginBackground(@RequestParam("file") MultipartFile file) {
        adminGuard.requireAdmin();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择背景图片");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("背景图片不能超过 10 MB");
        String contentType = String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        String extension = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(contentType)
                || !Arrays.asList("jpg", "jpeg", "png", "webp").contains(extension)) {
            throw new IllegalArgumentException("背景图片仅支持 JPEG、PNG 或 WebP");
        }
        String path = FastDFSClient.uploadFile(file);
        if (path == null || path.trim().isEmpty()) throw new IllegalStateException("背景图片上传失败，请稍后重试");
        return Response.makeOKRsp(Collections.singletonMap("loginBackgroundUrl", FastDFSClient.getResAccessUrl(path)));
    }

    @PostMapping("/settings/platform-logo")
    public ResponseResult<Object> uploadPlatformLogo(@RequestParam("file") MultipartFile file) {
        adminGuard.requireAdmin();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择平台 Logo");
        if (file.getSize() > 2L * 1024 * 1024) throw new IllegalArgumentException("平台 Logo 不能超过 2 MB");
        String contentType = String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        String extension = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("image/jpeg", "image/png", "image/webp", "image/svg+xml").contains(contentType)
                || !Arrays.asList("jpg", "jpeg", "png", "webp", "svg").contains(extension)) {
            throw new IllegalArgumentException("平台 Logo 仅支持 JPEG、PNG、WebP 或 SVG");
        }
        String path = FastDFSClient.uploadFile(file);
        if (path == null || path.trim().isEmpty()) throw new IllegalStateException("平台 Logo 上传失败，请稍后重试");
        return Response.makeOKRsp(Collections.singletonMap("platformLogoUrl", FastDFSClient.getResAccessUrl(path)));
    }

    @PutMapping("/content")
    public ResponseResult<Object> setCompetitionContent(@RequestBody CompetitionContentRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(settingService.setCompetitionContent(
                request == null ? null : request.getSections(), StpUtil.getLoginIdAsInt()));
    }

    @PutMapping("/help")
    public ResponseResult<Object> setCompetitionHelp(@RequestBody CompetitionHelpRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(Collections.singletonMap("competitionHelpContent",
                settingService.setCompetitionHelpContent(
                        request == null ? null : request.getContent(), StpUtil.getLoginIdAsInt())));
    }
}
