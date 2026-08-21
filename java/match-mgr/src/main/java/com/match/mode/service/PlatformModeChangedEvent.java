package com.match.mode.service;

public final class PlatformModeChangedEvent {
    private final String sourceMode;
    private final String targetMode;
    private final long generation;
    private final int actorUserId;
    private final String actorRole;

    public PlatformModeChangedEvent(String sourceMode, String targetMode, long generation,
                                    int actorUserId, String actorRole) {
        this.sourceMode = sourceMode;
        this.targetMode = targetMode;
        this.generation = generation;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public String getTargetMode() {
        return targetMode;
    }

    public long getGeneration() {
        return generation;
    }

    public int getActorUserId() {
        return actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }
}
