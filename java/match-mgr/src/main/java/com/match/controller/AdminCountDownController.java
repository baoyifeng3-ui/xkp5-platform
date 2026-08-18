package com.match.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.match.dto.CountDownActionRequest;
import com.match.security.AdminGuard;
import com.match.service.impl.CountDownServiceImpl;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/countdown")
public class AdminCountDownController {
    private final CountDownServiceImpl countDownService;
    private final AdminGuard adminGuard;

    public AdminCountDownController(CountDownServiceImpl countDownService, AdminGuard adminGuard) {
        this.countDownService = countDownService;
        this.adminGuard = adminGuard;
    }

    @PostMapping
    public ResponseResult<Object> apply(@RequestBody CountDownActionRequest request) {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(countDownService.apply(request, StpUtil.getLoginIdAsInt()));
    }
}
