package com.match.service.impl;

import com.match.dto.CountDownResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ParticipantLoginGate {
    private final CountDownServiceImpl countDownService;
    private final boolean gateEnabled;

    public ParticipantLoginGate(CountDownServiceImpl countDownService,
                                @Value("${match.participant-login-gate-enabled:true}") boolean gateEnabled) {
        this.countDownService = countDownService;
        this.gateEnabled = gateEnabled;
    }

    public String deniedMessage(boolean admin) {
        if (admin) {
            return null;
        }
        if (!gateEnabled) {
            return null;
        }
        CountDownResponse countdown = countDownService.snapshot();
        String status = countdown == null ? null : countdown.getStatus();
        if (status == null || "NOT_STARTED".equals(status)
                || "SCHEDULED".equals(status) || "RUNNING".equals(status) || "PAUSED".equals(status)) {
            return null;
        }
        if ("WAITING_LOGIN".equals(status)) {
            return "尚未到开放登录时间，请稍后再试";
        }
        if ("FINISHED".equals(status) || "ENDED".equals(status)) {
            return "比赛已结束，无法登录";
        }
        return "比赛尚未开始，暂时无法登录";
    }
}
