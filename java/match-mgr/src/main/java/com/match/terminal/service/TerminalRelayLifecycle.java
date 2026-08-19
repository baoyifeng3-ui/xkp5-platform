package com.match.terminal.service;

import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface TerminalRelayLifecycle {
    void closePersistedSession(String sessionId, Runnable persistenceClose);

    default boolean closePersistedSessionIf(String sessionId, BooleanSupplier persistenceClose) {
        return persistenceClose.getAsBoolean();
    }
}
