package com.match.controller;

import com.match.dto.NoticeRequest;
import com.match.entity.Notice;
import com.match.security.AdminGuard;
import com.match.service.NoticeStrive;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/admin/notice")
public class AdminNoticeController {
    private final NoticeStrive noticeService;
    private final AdminGuard adminGuard;

    public AdminNoticeController(NoticeStrive noticeService, AdminGuard adminGuard) {
        this.noticeService = noticeService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseResult<Object> get() {
        adminGuard.requireAdmin();
        Notice notice = noticeService.getNotice();
        return Response.makeOKRsp(Collections.singletonMap("noticeContent",
                notice == null || notice.getNoticeContent() == null ? "" : notice.getNoticeContent()));
    }

    @PutMapping
    public ResponseResult<Object> save(@RequestBody NoticeRequest request) {
        adminGuard.requireAdmin();
        Notice notice = noticeService.saveNotice(request == null ? null : request.getNoticeContent());
        return Response.makeOKRsp(Collections.singletonMap("noticeContent", notice.getNoticeContent()));
    }
}
