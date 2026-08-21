package com.match.security;

public class ParticipantModeException extends RuntimeException {
    public static final String PLATFORM_MODE_CHANGED = "PLATFORM_MODE_CHANGED";
    public static final String WRONG_PLATFORM_MODE = "WRONG_PLATFORM_MODE";

    private final String reasonCode;

    private ParticipantModeException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static ParticipantModeException generationMismatch() {
        return new ParticipantModeException(
                PLATFORM_MODE_CHANGED, "平台模式已切换，请重新登录");
    }

    public static ParticipantModeException wrongMode() {
        return new ParticipantModeException(
                WRONG_PLATFORM_MODE, "当前平台模式不允许访问此功能");
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public boolean isGenerationMismatch() {
        return PLATFORM_MODE_CHANGED.equals(reasonCode);
    }
}
