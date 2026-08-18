package com.match.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.match.dto.AnnouncementFieldRequest;
import com.match.security.AdminGuard;
import com.match.service.impl.AnnouncementFieldService;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/announcement-fields")
public class AdminAnnouncementFieldController {
    private final AnnouncementFieldService fieldService;
    private final AdminGuard adminGuard;

    public AdminAnnouncementFieldController(AnnouncementFieldService fieldService, AdminGuard adminGuard) {
        this.fieldService = fieldService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseResult<Object> list() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(fieldService.list());
    }

    @PostMapping
    public ResponseResult<Object> create(@RequestBody AnnouncementFieldRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(fieldService.create(
                request == null ? null : request.getFieldName(), StpUtil.getLoginIdAsInt()));
    }

    @PutMapping("/{fieldId}")
    public ResponseResult<Object> update(@PathVariable Integer fieldId,
                                         @RequestBody AnnouncementFieldRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(fieldService.update(fieldId,
                request == null ? null : request.getFieldName(),
                request == null ? null : request.getEnabled(), StpUtil.getLoginIdAsInt()));
    }
}
