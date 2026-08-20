package com.match.mode.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ModeTransitionView {
    private String transitionId;
    private String agentId;
    private String sourceMode;
    private String targetMode;
    private String state;
    private String failureSummary;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private final List<ModeTransitionStepView> steps = new ArrayList<>();
}
