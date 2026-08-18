package com.match.security;

import cn.dev33.satoken.stp.StpUtil;
import com.match.dto.CountDownResponse;
import com.match.entity.User;
import com.match.service.impl.CountDownServiceImpl;
import com.match.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ParticipantAccessGuard {
    private final UserServiceImpl userService;
    private final CountDownServiceImpl countDownService;
    private final String adminUserName;

    public ParticipantAccessGuard(UserServiceImpl userService, CountDownServiceImpl countDownService,
                                  @Value("${match.admin-user-name:admin}") String adminUserName) {
        this.userService = userService;
        this.countDownService = countDownService;
        this.adminUserName = adminUserName;
    }

    public void requireCompetitionStarted() {
        User user = userService.getById(StpUtil.getLoginIdAsInt());
        if (user != null && adminUserName.equals(user.getUserName())) return;
        CountDownResponse snapshot = countDownService.snapshot();
        if ("PRE_START".equals(snapshot.getAccessPhase()) || "BEFORE_LOGIN".equals(snapshot.getAccessPhase())) {
            throw new CompetitionAccessException("比赛尚未正式开始，当前功能暂不可用");
        }
        if ("FINISHED".equals(snapshot.getAccessPhase())) {
            throw new CompetitionAccessException("比赛已结束，当前功能不可用");
        }
    }

    public User requireParticipant() {
        StpUtil.checkLogin();
        User user = userService.getById(StpUtil.getLoginIdAsInt());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new CompetitionAccessException("当前比赛账号不可用");
        }
        if (Boolean.TRUE.equals(user.getIsAdmin()) || adminUserName.equalsIgnoreCase(user.getUserName())) {
            throw new CompetitionAccessException("管理员只能预览试卷，不能保存或提交");
        }
        if ("FINISHED".equals(countDownService.snapshot().getAccessPhase())) {
            throw new CompetitionAccessException("比赛已结束，当前功能不可用");
        }
        return user;
    }
}
