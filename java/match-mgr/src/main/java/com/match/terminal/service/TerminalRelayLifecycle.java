package com.match.terminal.service;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface TerminalRelayLifecycle {
    enum ConditionalCloseDecision { PERSISTED, LOCAL_ONLY, KEEP_OPEN }
    enum ConditionalCloseResult { PERSISTED, LOCAL_ONLY, KEPT_OPEN }

    void closePersistedSession(String sessionId, Runnable persistenceClose);

    default boolean closePersistedSessionIf(String sessionId, BooleanSupplier persistenceClose) {
        return persistenceClose.getAsBoolean();
    }

    default ConditionalCloseResult closePersistedSessionConditionally(
            String sessionId, Supplier<ConditionalCloseDecision> persistenceClose) {
        ConditionalCloseDecision decision = persistenceClose.get();
        return decision == ConditionalCloseDecision.PERSISTED
                ? ConditionalCloseResult.PERSISTED
                : decision == ConditionalCloseDecision.LOCAL_ONLY
                ? ConditionalCloseResult.LOCAL_ONLY : ConditionalCloseResult.KEPT_OPEN;
    }
}
