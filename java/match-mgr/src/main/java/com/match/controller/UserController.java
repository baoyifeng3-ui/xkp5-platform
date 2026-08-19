package com.match.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.dto.PasswordChangeRequest;
import com.match.dashboard.service.UserActivityService;
import com.match.entity.User;
import com.match.security.UserRole;
import com.match.security.PasswordCodec;
import com.match.service.impl.UserServiceImpl;
import com.match.service.impl.ParticipantLoginGate;
import com.match.util.result.Response;
import com.match.util.result.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserServiceImpl userService;
    private final ParticipantLoginGate participantLoginGate;
    private final PasswordCodec passwordCodec;
    private final UserActivityService userActivityService;

    public UserController(UserServiceImpl userService,
                          ParticipantLoginGate participantLoginGate,
                          PasswordCodec passwordCodec,
                          UserActivityService userActivityService) {
        this.userService = userService;
        this.participantLoginGate = participantLoginGate;
        this.passwordCodec = passwordCodec;
        this.userActivityService = userActivityService;
    }

    @PostMapping("login")
    public ResponseResult<Object> login(User input) {
        if (input.getUserName() == null || input.getPassword() == null) {
            return Response.makeRsp(400, "请输入用户名和密码");
        }
        User user = userService.getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUserName, input.getUserName()));
        if (user == null || !passwordCodec.matches(input.getPassword(), user.getPassword())) {
            return Response.makeRsp(400, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return Response.makeRsp(403, "账号已停用，请联系管理员");
        }
        String deniedMessage = participantLoginGate.deniedMessage(isAdmin(user));
        if (deniedMessage != null) {
            return Response.makeRsp(403, deniedMessage);
        }

        if (isAdmin(user) && !passwordCodec.isEncoded(user.getPassword())) {
            user.setPassword(passwordCodec.encode(input.getPassword()));
            userService.updateById(user);
        }

        StpUtil.login(user.getUserId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        userActivityService.recordLogin(user.getUserId(), tokenInfo.getTokenValue(), StpUtil.getTokenTimeout());
        Map<String, Object> data = currentUserData(user);
        data.put("tokenName", tokenInfo.getTokenName());
        data.put("tokenValue", tokenInfo.getTokenValue());
        data.put("loginId", tokenInfo.getLoginId());
        return Response.makeOKRsp(data);
    }

    @GetMapping("me")
    public ResponseResult<Object> currentUser() {
        User user = userService.getById(StpUtil.getLoginIdAsInt());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            StpUtil.logout();
            return Response.makeRsp(401, "账号不存在或已停用");
        }
        return Response.makeOKRsp(currentUserData(user));
    }

    @PostMapping("change-password")
    public ResponseResult<Object> changePassword(@RequestBody PasswordChangeRequest request) {
        User user = userService.getById(StpUtil.getLoginIdAsInt());
        if (request.getCurrentPassword() == null || !passwordCodec.matches(request.getCurrentPassword(), user.getPassword())) {
            return Response.makeRsp(400, "当前密码错误");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().length() < 6) {
            return Response.makeRsp(400, "新密码至少需要 6 个字符");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            return Response.makeRsp(400, "新密码不能与当前密码相同");
        }
        user.setPassword(isAdmin(user) ? passwordCodec.encode(request.getNewPassword()) : request.getNewPassword());
        user.setMustChangePassword(false);
        userService.updateById(user);
        return Response.makeOKRsp("密码修改成功");
    }

    @PostMapping("logout")
    public ResponseResult<Object> logout() {
        userActivityService.logout(StpUtil.getTokenValue());
        StpUtil.logout();
        return Response.makeOKRsp("已退出登录");
    }

    @PostMapping("activity")
    public ResponseResult<Object> activity() {
        userActivityService.touch(StpUtil.getLoginIdAsInt(), StpUtil.getTokenValue(), StpUtil.getTokenTimeout());
        return Response.makeOKRsp("ok");
    }

    private Map<String, Object> currentUserData(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getUserId());
        data.put("userName", user.getUserName());
        data.put("admin", isAdmin(user));
        data.put("role", roleOf(user).name());
        data.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        return data;
    }

    private boolean isAdmin(User user) {
        return roleOf(user) != UserRole.USER;
    }

    private UserRole roleOf(User user) {
        return UserRole.resolve(user.getRole(), user.getIsAdmin(), user.getUserName());
    }
}
