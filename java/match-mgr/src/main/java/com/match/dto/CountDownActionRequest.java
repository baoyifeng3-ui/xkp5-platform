package com.match.dto;

import lombok.Data;

@Data
public class CountDownActionRequest {
    private String action;
    private Integer minutes;
    private Long endTime;
    private Long startTime;
    private Integer preLoginMinutes;
    private Integer durationMinutes;
}
