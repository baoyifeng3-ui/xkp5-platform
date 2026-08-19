package com.match.terminal.relay;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.terminal.service.TerminalSessionService;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

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

public class TerminalRelayCoordinator {
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
    private static final long RATE_BYTES_PER_SECOND = 2L * 1024 * 1024;
    private static final long RATE_BURST_BYTES = 8L * 1024 * 1024;
    private static final ObjectMapper CONTROL_JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final TerminalSessionService sessions;
    private final Executor writerExecutor;
    private final LongSupplier ticker;
    private final ConcurrentHashMap<String, Relay> relays = new ConcurrentHashMap<>();

    public TerminalRelayCoordinator(TerminalSessionService sessions, Executor writerExecutor,
                                    LongSupplier ticker) {
        this.sessions = sessions;
        this.writerExecutor = writerExecutor;
        this.ticker = ticker;
    }

    public boolean attach(String sessionId, TerminalPeer peer) {
        if (sessionId == null || peer == null || peer.session() == null) {
            return false;
        }
        Relay relay;
        synchronized (relays) {
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
        boolean activate;
        synchronized (relay) {
            if (relay.closed.get() || relay.peer(peer.role()) != null) {
                peer.close(policyStatus(CloseReason.SESSION_REJECTED));
                return false;
            }
            relay.setPeer(peer);
            activate = relay.browser != null && relay.agent != null;
        }
        if (activate) {
            try {
                if (!sessions.markRelayActive(sessionId)) {
                    close(relay, CloseReason.SESSION_REJECTED);
                    return false;
                }
            } catch (RuntimeException exception) {
                close(relay, CloseReason.SESSION_REJECTED);
                return false;
            }
        }
        return true;
    }

    public void onBinary(TerminalPeer source, ByteBuffer payload) {
        Relay relay = relayFor(source);
        if (relay == null || payload == null || payload.remaining() > MAX_BINARY_BYTES) {
            closeIfPresent(relay, CloseReason.PROTOCOL_ERROR);
            return;
        }
        int size = payload.remaining();
        if (!relay.bucket(source.role()).tryConsume(size, ticker.getAsLong())) {
            close(relay, CloseReason.RATE_LIMITED);
            return;
        }
        byte[] copy = new byte[size];
        payload.slice().get(copy);
        if (!forward(relay, source, new BinaryMessage(copy))) {
            close(relay, CloseReason.BACKPRESSURE);
            return;
        }
        boolean trafficRecorded = true;
        synchronized (relay) {
            if (source.role() == TerminalPeer.Role.BROWSER) {
                relay.browserToAgent += size;
            } else {
                relay.agentToBrowser += size;
            }
            if (relay.browserToAgent + relay.agentToBrowser >= TRAFFIC_FLUSH_BYTES) {
                trafficRecorded = flushTraffic(relay);
            }
        }
        if (!trafficRecorded) {
            close(relay, CloseReason.SESSION_REJECTED);
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
        if (!forward(relay, source, new TextMessage(payload))) {
            close(relay, CloseReason.BACKPRESSURE);
            return;
        }
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

    private boolean forward(Relay relay, TerminalPeer source, WebSocketMessage<?> message) {
        TerminalPeer target = source.role() == TerminalPeer.Role.BROWSER ? relay.agent : relay.browser;
        return target != null && target.enqueue(message, writerExecutor,
                () -> close(relay, CloseReason.IO_ERROR));
    }

    private void closeIfPresent(Relay relay, CloseReason reason) {
        if (relay != null) {
            close(relay, reason);
        }
    }

    private void close(Relay relay, CloseReason reason) {
        if (!relay.closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (relays) {
            relays.remove(relay.sessionId, relay);
        }
        synchronized (relay) {
            flushTraffic(relay);
        }
        CloseStatus status = reason.operator
                ? new CloseStatus(1000, reason.name()) : policyStatus(reason);
        if (relay.browser != null) {
            relay.browser.close(status);
        }
        if (relay.agent != null) {
            relay.agent.close(status);
        }
        sessions.finishRelay(relay.sessionId, reason.operator, reason.name());
    }

    private boolean flushTraffic(Relay relay) {
        long browserBytes = relay.browserToAgent;
        long agentBytes = relay.agentToBrowser;
        if (browserBytes == 0 && agentBytes == 0) {
            return true;
        }
        relay.browserToAgent = 0;
        relay.agentToBrowser = 0;
        return sessions.recordRelayTraffic(relay.sessionId, browserBytes, agentBytes);
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
        private TerminalPeer browser;
        private TerminalPeer agent;
        private long browserToAgent;
        private long agentToBrowser;

        private Relay(String sessionId, long now) {
            this.sessionId = sessionId;
            this.browserRate = new TokenBucket(now);
            this.agentRate = new TokenBucket(now);
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
