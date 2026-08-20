package com.match.dashboard.web;

import com.match.dashboard.model.DashboardOverview;
import com.match.dashboard.service.DashboardOverviewService;
import com.match.security.AdminGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {
    private final AdminGuard adminGuard;
    private final DashboardOverviewService overviewService;

    public AdminDashboardController(AdminGuard adminGuard, DashboardOverviewService overviewService) {
        this.adminGuard = adminGuard;
        this.overviewService = overviewService;
    }

    @GetMapping("overview")
    public ResponseResult<DashboardOverview> overview() {
        adminGuard.requireAdmin();
        return Response.makeOKRsp(overviewService.snapshot());
    }
}
