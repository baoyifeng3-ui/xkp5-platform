package com.match.mode.model;

import lombok.Data;

@Data
public class ModeTransitionStepView {
    private String stepId;
    private Integer phaseNumber;
    private Integer stepOrdinal;
    private String environmentKind;
    private String environmentId;
    private String actionType;
    private String state;
    private String resultCode;
    private String resultMessage;
}
