package com.match.attendance.web;
import cn.dev33.satoken.stp.StpUtil;
import com.match.attendance.service.AttendanceService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {
    private final AttendanceService service; private final RoleGuard roles;
    public AttendanceController(AttendanceService service,RoleGuard roles){this.service=service;this.roles=roles;}
    @PostMapping("/start") public ResponseResult<Object> start(){roles.requireAnyAdmin();return Response.makeOKRsp(service.start());}
    @PostMapping("/end") public ResponseResult<Object> end(){roles.requireAnyAdmin();service.end();return Response.makeOKRsp(null);}
    @GetMapping("/status") public ResponseResult<Object> status(){return Response.makeOKRsp(service.status(StpUtil.isLogin()?StpUtil.getLoginIdAsInt():null));}
    @PostMapping("/check-in") public ResponseResult<Object> checkIn(){StpUtil.checkLogin();return Response.makeOKRsp(service.checkIn(StpUtil.getLoginIdAsInt()));}
}
