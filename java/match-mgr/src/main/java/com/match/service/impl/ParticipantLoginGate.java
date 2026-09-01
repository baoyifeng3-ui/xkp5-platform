package com.match.service.impl;

import com.match.dto.CountDownResponse;
import com.match.mode.service.PlatformModeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ParticipantLoginGate {
    private final CountDownServiceImpl countDownService;
    private final PlatformModeService platformModeService;
    private final boolean gateEnabled;

    public ParticipantLoginGate(CountDownServiceImpl countDownService,
                                PlatformModeService platformModeService,
                                @Value("${match.participant-login-gate-enabled:true}") boolean gateEnabled) {
        this.countDownService = countDownService;
        this.platformModeService = platformModeService;
        this.gateEnabled = gateEnabled;
    }

    public String deniedMessage(boolean admin) {
        return null;
    }
}
