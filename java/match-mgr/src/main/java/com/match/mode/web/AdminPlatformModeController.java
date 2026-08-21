package com.match.mode.web;

import com.match.entity.User;
import com.match.mode.model.ChangePlatformModeRequest;
import com.match.mode.service.ModeTransitionService;
import com.match.mode.service.PlatformModeService;
import com.match.security.RoleGuard;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/platform-mode")
public class AdminPlatformModeController {
    private static final int MAX_TRANSITIONS = 100;

    private final RoleGuard roleGuard;
    private final PlatformModeService platformModeService;
    private final ModeTransitionService transitionService;

    public AdminPlatformModeController(RoleGuard roleGuard,
                                       PlatformModeService platformModeService,
                                       ModeTransitionService transitionService) {
        this.roleGuard = roleGuard;
        this.platformModeService = platformModeService;
        this.transitionService = transitionService;
    }

    @GetMapping("")
    public ResponseResult<Object> current() {
        roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(platformModeService.current());
    }

    @PostMapping("")
    public ResponseResult<Object> change(@RequestBody(required = false) ChangePlatformModeRequest request) {
        User actor = roleGuard.requireBusinessAdmin();
        requireConfirmation(request);
        return Response.makeOKRsp(platformModeService.change(request.getTargetMode(), actor));
    }

    @GetMapping("/transitions")
    public ResponseResult<Object> transitions(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(transitionService.list(Math.max(1, Math.min(MAX_TRANSITIONS, limit))));
    }

    @GetMapping("/transitions/{transitionId}")
    public ResponseResult<Object> transition(@PathVariable String transitionId) {
        roleGuard.requireBusinessAdmin();
        return Response.makeOKRsp(transitionService.get(transitionId));
    }

    private void requireConfirmation(ChangePlatformModeRequest request) {
        String expected = request != null && PlatformModeService.COMPETITION.equals(request.getTargetMode())
                ? "ENTER COMPETITION" : "EXIT COMPETITION";
        if (request == null || (!PlatformModeService.COMPETITION.equals(request.getTargetMode())
                && !PlatformModeService.TRAINING.equals(request.getTargetMode()))
                || !expected.equals(request.getConfirmation())) {
            throw new IllegalArgumentException("模式切换确认文本不正确");
        }
    }
}
