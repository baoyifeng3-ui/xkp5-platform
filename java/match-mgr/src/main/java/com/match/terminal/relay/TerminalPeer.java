package com.match.terminal.relay;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TerminalPeer {
    public enum Role { BROWSER, AGENT }

    static final int MAX_QUEUED_BYTES = 1024 * 1024;
    static final int MAX_QUEUED_MESSAGES = 1024;

    private final Role role;
    private final String agentId;
    private final Deque<QueuedMessage> outbound = new ArrayDeque<>();
    private final AtomicBoolean writing = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile WebSocketSession session;
    private int queuedBytes;

    public TerminalPeer(Role role, String agentId) {
        this(role, agentId, null);
    }

    public TerminalPeer(Role role, String agentId, WebSocketSession session) {
        if (role == null) {
            throw new IllegalArgumentException("Terminal peer role is required");
        }
        this.role = role;
        this.agentId = agentId;
        this.session = session;
    }

    public Role role() {
        return role;
    }

    public String agentId() {
        return agentId;
    }

    public WebSocketSession session() {
        return session;
    }

    public void bind(WebSocketSession socket) {
        if (socket == null || (session != null && session != socket)) {
            throw new IllegalStateException("Terminal peer is already bound");
        }
        session = socket;
    }

    boolean enqueue(WebSocketMessage<?> message, Executor executor, Runnable failed) {
        int size = messageSize(message);
        synchronized (outbound) {
            if (closed.get() || outbound.size() >= MAX_QUEUED_MESSAGES
                    || size > MAX_QUEUED_BYTES || queuedBytes > MAX_QUEUED_BYTES - size) {
                return false;
            }
            outbound.addLast(new QueuedMessage(message, size));
            queuedBytes += size;
        }
        schedule(executor, failed);
        return !closed.get();
    }

    void close(CloseStatus status) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (outbound) {
            outbound.clear();
            queuedBytes = 0;
        }
        WebSocketSession socket = session;
        if (socket != null) {
            try {
                socket.close(status);
            } catch (IOException | RuntimeException ignored) {
                // The relay is already terminal; close failures cannot be recovered here.
            }
        }
    }

    private void schedule(Executor executor, Runnable failed) {
        if (!writing.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> drain(executor, failed));
        } catch (RejectedExecutionException exception) {
            writing.set(false);
            failed.run();
        }
    }

    private void drain(Executor executor, Runnable failed) {
        try {
            while (!closed.get()) {
                QueuedMessage queued;
                synchronized (outbound) {
                    queued = outbound.pollFirst();
                    if (queued == null) {
                        writing.set(false);
                        return;
                    }
                }
                WebSocketSession socket = session;
                if (socket == null || !socket.isOpen()) {
                    failed.run();
                    return;
                }
                socket.sendMessage(queued.message);
                synchronized (outbound) {
                    if (!closed.get()) {
                        queuedBytes -= queued.size;
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            writing.set(false);
            failed.run();
            return;
        }
        if (closed.get()) {
            writing.set(false);
        }
    }

    private int messageSize(WebSocketMessage<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8).length;
        }
        return message.getPayloadLength();
    }

    private static final class QueuedMessage {
        private final WebSocketMessage<?> message;
        private final int size;

        private QueuedMessage(WebSocketMessage<?> message, int size) {
            this.message = message;
            this.size = size;
        }
    }
}
