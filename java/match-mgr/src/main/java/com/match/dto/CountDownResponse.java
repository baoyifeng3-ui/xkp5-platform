package com.match.dto;

import lombok.Data;

@Data
public class CountDownResponse {
    private long serverTime;
    private Long startTime;
    private Long endTime;
    private Long scheduledStartTime;
    private Long loginOpenTime;
    private String status;
    private String accessPhase;
    private long remainingSeconds;
    private long preStartRemainingSeconds;
    private Integer preLoginMinutes;
    private Integer durationMinutes;
}
