package com.match.mode.service;

final class ModeTransitionDispatchException extends IllegalStateException {
    private final String transitionId;
    private final String stepId;
    private final String failureMessage;

    ModeTransitionDispatchException(String transitionId, String stepId,
                                    String failureMessage, Throwable cause) {
        super("MODE_COMMAND_DISPATCH_FAILED", cause);
        this.transitionId = transitionId;
        this.stepId = stepId;
        this.failureMessage = failureMessage;
    }

    String getTransitionId() {
        return transitionId;
    }

    String getStepId() {
        return stepId;
    }

    String getFailureMessage() {
        return failureMessage;
    }
}
