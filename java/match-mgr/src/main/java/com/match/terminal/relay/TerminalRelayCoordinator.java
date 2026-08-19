package com.match.terminal.relay;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.terminal.service.TerminalSessionService;
import com.match.terminal.service.TerminalRelayLifecycle;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public class TerminalRelayCoordinator implements TerminalRelayLifecycle {
    @FunctionalInterface
    public interface PeriodicScheduler {
        void schedule(Runnable task);
    }

    public enum CloseReason {
        OPERATOR_CLOSED(true), PEER_DISCONNECTED(false), PROTOCOL_ERROR(false),
        BACKPRESSURE(false), RATE_LIMITED(false), SESSION_REJECTED(false), IO_ERROR(false);

        private final boolean operator;

        CloseReason(boolean operator) {
            this.operator = operator;
        }
    }

    public static final int MAX_BINARY_BYTES = 64 * 1024;
    public static final int MAX_TEXT_BYTES = 4 * 1024;
    public static final int MAX_RELAYS = 1024;
    private static final long TRAFFIC_FLUSH_BYTES = 256 * 1024;
    private static final long TRAFFIC_FLUSH_NANOS = 1_000_000_000L;
    private static final long RATE_BYTES_PER_SECOND = 2L * 1024 * 1024;
    private static final long RATE_BURST_BYTES = 8L * 1024 * 1024;
    private static final ObjectMapper CONTROL_JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final TerminalSessionService sessions;
    private final Executor writerExecutor;
    private final LongSupplier ticker;
    private final ConcurrentHashMap<String, Relay> relays = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();

    public TerminalRelayCoordinator(TerminalSessionService sessions, Executor writerExecutor,
                                    LongSupplier ticker) {
        this(sessions, writerExecutor, ticker, task -> { });
    }

    public TerminalRelayCoordinator(TerminalSessionService sessions, Executor writerExecutor,
                                    LongSupplier ticker, PeriodicScheduler scheduler) {
        this.sessions = sessions;
        this.writerExecutor = writerExecutor;
        this.ticker = ticker;
        scheduler.schedule(this::flushDueTraffic);
    }

    public boolean attach(String sessionId, TerminalPeer peer) {
        if (sessionId == null || peer == null || peer.session() == null) {
            return false;
        }
        Relay relay;
        synchronized (lifecycleLock) {
            if (!sessions.isRelayAttachmentEligible(sessionId, peer.role().name(), peer.agentId())) {
                peer.close(policyStatus(CloseReason.SESSION_REJECTED));
                return false;
            }
            relay = relays.get(sessionId);
            if (relay == null) {
                if (relays.size() >= MAX_RELAYS) {
                    peer.close(policyStatus(CloseReason.SESSION_REJECTED));
                    return false;
                }
                relay = new Relay(sessionId, ticker.getAsLong());
                relays.put(sessionId, relay);
            }
        }
        boolean activationFailed = false;
        synchronized (relay) {
            if (relay.closed.get() || relay.peer(peer.role()) != null) {
                peer.close(policyStatus(CloseReason.SESSION_REJECTED));
                return false;
            }
            relay.setPeer(peer);
            if (relay.browser != null && relay.agent != null) {
                try {
                    if (sessions.markRelayActive(sessionId)) {
                        relay.active = true;
                    } else {
                        activationFailed = true;
                    }
                } catch (RuntimeException exception) {
                    activationFailed = true;
                }
            }
        }
        if (activationFailed) {
            close(relay, CloseReason.SESSION_REJECTED);
            return false;
        }
        return true;
    }

    public void onBinary(TerminalPeer source, ByteBuffer payload) {
        Relay relay = relayFor(source);
        if (relay == null || payload == null || payload.remaining() > MAX_BINARY_BYTES) {
            closeIfPresent(relay, CloseReason.PROTOCOL_ERROR);
            return;
        }
        if (!relay.active) {
            if (!relay.bucket(source.role()).tryConsume(payload.remaining(), ticker.getAsLong())) {
                close(relay, CloseReason.RATE_LIMITED);
            }
            return;
        }
        int size = payload.remaining();
        byte[] copy = new byte[size];
        payload.slice().get(copy);
        TerminalPeer scheduledPeer = null;
        CloseReason closeReason = null;
        boolean trafficRecorded = true;
        synchronized (relay) {
            if (relay.closed.get() || !relay.active) {
                return;
            }
            if (!relay.bucket(source.role()).tryConsume(size, ticker.getAsLong())) {
                closeReason = CloseReason.RATE_LIMITED;
            } else {
                TerminalPeer target = oppositePeer(relay, source);
                if (target == null || !target.offer(new BinaryMessage(copy))) {
                    closeReason = CloseReason.BACKPRESSURE;
                } else {
                    scheduledPeer = target;
                    if (source.role() == TerminalPeer.Role.BROWSER) {
                        relay.browserToAgent += size;
                    } else {
                        relay.agentToBrowser += size;
                    }
                    if (relay.browserToAgent + relay.agentToBrowser >= TRAFFIC_FLUSH_BYTES) {
                        trafficRecorded = flushTraffic(relay, ticker.getAsLong());
                    }
                }
            }
        }
        if (scheduledPeer != null) {
            scheduledPeer.scheduleWriter(writerExecutor, () -> close(relay, CloseReason.IO_ERROR));
        }
        if (!trafficRecorded) {
            closeReason = CloseReason.SESSION_REJECTED;
        }
        if (closeReason != null) {
            close(relay, closeReason);
        }
    }

    public void onText(TerminalPeer source, String payload) {
        Relay relay = relayFor(source);
        if (relay == null || payload == null
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            closeIfPresent(relay, CloseReason.PROTOCOL_ERROR);
            return;
        }
        Control control = parseControl(payload);
        if (control == null) {
            close(relay, CloseReason.PROTOCOL_ERROR);
            return;
        }
        if (control.close && source.role() != TerminalPeer.Role.BROWSER) {
            close(relay, CloseReason.PROTOCOL_ERROR);
            return;
        }
        if (!relay.active) {
            return;
        }
        TerminalPeer scheduledPeer;
        synchronized (relay) {
            if (relay.closed.get() || !relay.active) {
                return;
            }
            TerminalPeer target = oppositePeer(relay, source);
            if (target == null || !target.offer(new TextMessage(payload))) {
                scheduledPeer = null;
            } else {
                scheduledPeer = target;
            }
        }
        if (scheduledPeer == null) {
            close(relay, CloseReason.BACKPRESSURE);
            return;
        }
        scheduledPeer.scheduleWriter(writerExecutor, () -> close(relay, CloseReason.IO_ERROR));
        if (control.close) {
            close(relay, CloseReason.OPERATOR_CLOSED);
        }
    }

    public void detach(TerminalPeer peer, CloseReason reason) {
        Relay relay = relayFor(peer);
        if (relay != null) {
            close(relay, reason == null ? CloseReason.PEER_DISCONNECTED : reason);
        }
    }

    public int relayCount() {
        return relays.size();
    }

    @Override
    public void closePersistedSession(String sessionId, Runnable persistenceClose) {
        Relay relay;
        synchronized (lifecycleLock) {
            persistenceClose.run();
            relay = relays.get(sessionId);
            if (relay != null && !terminateRelay(relay)) {
                relay = null;
            } else if (relay != null) {
                relays.remove(relay.sessionId, relay);
            }
        }
        if (relay != null) {
            closePeer(relay.browser, CloseReason.OPERATOR_CLOSED);
            closePeer(relay.agent, CloseReason.OPERATOR_CLOSED);
        }
    }

    private Relay relayFor(TerminalPeer peer) {
        if (peer == null) {
            return null;
        }
        for (Relay relay : relays.values()) {
            if (relay.browser == peer || relay.agent == peer) {
                return relay;
            }
        }
        return null;
    }

    private TerminalPeer oppositePeer(Relay relay, TerminalPeer source) {
        return source.role() == TerminalPeer.Role.BROWSER ? relay.agent : relay.browser;
    }

    private void closeIfPresent(Relay relay, CloseReason reason) {
        if (relay != null) {
            close(relay, reason);
        }
    }

    private void close(Relay relay, CloseReason reason) {
        boolean terminated;
        synchronized (lifecycleLock) {
            terminated = terminateRelay(relay);
            if (terminated) {
                try {
                    sessions.finishRelay(relay.sessionId, reason.operator, reason.name());
                    relays.remove(relay.sessionId, relay);
                } catch (RuntimeException ignored) {
                    relay.pendingFinishReason = reason;
                }
            }
        }
        if (!terminated) {
            return;
        }
        closePeer(relay.browser, reason);
        closePeer(relay.agent, reason);
    }

    private boolean terminateRelay(Relay relay) {
        synchronized (relay) {
            if (!relay.closed.compareAndSet(false, true)) {
                return false;
            }
            relay.active = false;
            flushTraffic(relay, ticker.getAsLong());
        }
        return true;
    }

    private boolean flushTraffic(Relay relay, long now) {
        long browserBytes = relay.browserToAgent;
        long agentBytes = relay.agentToBrowser;
        if (browserBytes == 0 && agentBytes == 0) {
            return true;
        }
        relay.browserToAgent = 0;
        relay.agentToBrowser = 0;
        relay.lastTrafficFlushNanos = now;
        try {
            return sessions.recordRelayTraffic(relay.sessionId, browserBytes, agentBytes);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void flushDueTraffic() {
        long now = ticker.getAsLong();
        for (Relay relay : relays.values()) {
            if (relay.closed.get()) {
                retryPendingFinish(relay);
                continue;
            }
            boolean recorded = true;
            synchronized (relay) {
                if (relay.active && relay.browserToAgent + relay.agentToBrowser > 0
                        && now - relay.lastTrafficFlushNanos >= TRAFFIC_FLUSH_NANOS) {
                    recorded = flushTraffic(relay, now);
                }
            }
            if (!recorded) {
                close(relay, CloseReason.SESSION_REJECTED);
            }
        }
    }

    private void retryPendingFinish(Relay relay) {
        synchronized (lifecycleLock) {
            CloseReason reason = relay.pendingFinishReason;
            if (reason == null || relays.get(relay.sessionId) != relay) {
                return;
            }
            try {
                sessions.finishRelay(relay.sessionId, reason.operator, reason.name());
                relay.pendingFinishReason = null;
                relays.remove(relay.sessionId, relay);
            } catch (RuntimeException ignored) {
                // The bounded closed entry continues rejecting delayed attachment until retry.
            }
        }
    }

    private void closePeer(TerminalPeer peer, CloseReason reason) {
        if (peer == null) {
            return;
        }
        CloseStatus status = reason.operator
                ? new CloseStatus(1000, reason.name()) : policyStatus(reason);
        try {
            peer.close(status);
        } catch (RuntimeException ignored) {
            // Continue closing the other peer and finalizing the session.
        }
    }

    private static CloseStatus policyStatus(CloseReason reason) {
        return new CloseStatus(1008, reason.name());
    }

    private Control parseControl(String payload) {
        try (JsonParser parser = CONTROL_JSON.getFactory().createParser(payload)) {
            JsonNode node = CONTROL_JSON.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) {
                return null;
            }
            JsonNode typeNode = node.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                return null;
            }
            String type = typeNode.textValue();
            if ("resize".equals(type)) {
                if (!hasOnly(node, "type", "columns", "rows")) {
                    return null;
                }
                JsonNode columns = node.get("columns");
                JsonNode rows = node.get("rows");
                if (columns == null || rows == null || !columns.isIntegralNumber()
                        || !rows.isIntegralNumber() || !columns.canConvertToInt()
                        || !rows.canConvertToInt()) {
                    return null;
                }
                int columnValue = columns.intValue();
                int rowValue = rows.intValue();
                return columnValue >= 20 && columnValue <= 500 && rowValue >= 5 && rowValue <= 200
                        ? new Control(false) : null;
            }
            if ("ping".equals(type) || "pong".equals(type)) {
                return hasOnly(node, "type") ? new Control(false) : null;
            }
            if ("close".equals(type)) {
                JsonNode reason = node.get("reason");
                return hasOnly(node, "type", "reason") && reason != null && reason.isTextual()
                        && "OPERATOR_CLOSED".equals(reason.textValue()) ? new Control(true) : null;
            }
            return null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private boolean hasOnly(JsonNode node, String... expected) {
        Set<String> names = new HashSet<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        return names.equals(new HashSet<>(Arrays.asList(expected)));
    }

    private static final class Control {
        private final boolean close;

        private Control(boolean close) {
            this.close = close;
        }
    }

    private static final class Relay {
        private final String sessionId;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final TokenBucket browserRate;
        private final TokenBucket agentRate;
        private volatile TerminalPeer browser;
        private volatile TerminalPeer agent;
        private volatile boolean active;
        private long browserToAgent;
        private long agentToBrowser;
        private long lastTrafficFlushNanos;
        private CloseReason pendingFinishReason;

        private Relay(String sessionId, long now) {
            this.sessionId = sessionId;
            this.browserRate = new TokenBucket(now);
            this.agentRate = new TokenBucket(now);
            this.lastTrafficFlushNanos = now;
        }

        private TerminalPeer peer(TerminalPeer.Role role) {
            return role == TerminalPeer.Role.BROWSER ? browser : agent;
        }

        private void setPeer(TerminalPeer peer) {
            if (peer.role() == TerminalPeer.Role.BROWSER) {
                browser = peer;
            } else {
                agent = peer;
            }
        }

        private TokenBucket bucket(TerminalPeer.Role role) {
            return role == TerminalPeer.Role.BROWSER ? browserRate : agentRate;
        }
    }

    private static final class TokenBucket {
        private long available = RATE_BURST_BYTES;
        private long lastNanos;

        private TokenBucket(long now) {
            lastNanos = now;
        }

        private synchronized boolean tryConsume(long bytes, long now) {
            if (now > lastNanos) {
                long elapsed = now - lastNanos;
                long refill = elapsed > Long.MAX_VALUE / RATE_BYTES_PER_SECOND
                        ? RATE_BURST_BYTES : elapsed * RATE_BYTES_PER_SECOND / 1_000_000_000L;
                available = refill >= RATE_BURST_BYTES - available
                        ? RATE_BURST_BYTES : available + refill;
                lastNanos = now;
            }
            if (bytes > available) {
                return false;
            }
            available -= bytes;
            return true;
        }
    }
}
