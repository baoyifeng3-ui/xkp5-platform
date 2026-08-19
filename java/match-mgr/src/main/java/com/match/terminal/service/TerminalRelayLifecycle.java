package com.match.terminal.service;

@FunctionalInterface
public interface TerminalRelayLifecycle {
    void closePersistedSession(String sessionId, Runnable persistenceClose);
}
