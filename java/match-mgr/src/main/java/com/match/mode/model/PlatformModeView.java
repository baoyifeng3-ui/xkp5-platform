package com.match.mode.model;

import java.time.LocalDateTime;

public final class PlatformModeView {
    private final String mode;
    private final long generation;
    private final Integer changedBy;
    private final LocalDateTime changedAt;

    public PlatformModeView(String mode, long generation, Integer changedBy,
                            LocalDateTime changedAt) {
        this.mode = mode;
        this.generation = generation;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public String getMode() {
        return mode;
    }

    public long getGeneration() {
        return generation;
    }

    public Integer getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
