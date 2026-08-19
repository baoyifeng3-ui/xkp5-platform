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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private enum TrafficPersistence {
        SUCCESS, TERMINAL, RETRY
    }

    public static final int MAX_BINARY_BYTES = 64 * 1024;
    public static final int MAX_TEXT_BYTES = 4 * 1024;
    public static final int MAX_RELAYS = 1024;
    static final int MAX_CLOSE_RETRIES_PER_TICK = 8;
    private static final long TRAFFIC_FLUSH_BYTES = 256 * 1024;
    private static final long TRAFFIC_FLUSH_NANOS = 1_000_000_000L;
    private static final long CLOSE_RETRY_INITIAL_NANOS = 1_000_000_000L;
    private static final long CLOSE_RETRY_MAX_NANOS = 30_000_000_000L;
    private static final long ELIGIBILITY_RECONCILE_NANOS = 5_000_000_000L;
    private static final int ELIGIBILITY_RECONCILE_BATCH = 100;
    private static final long RATE_BYTES_PER_SECOND = 2L * 1024 * 1024;
    private static final long RATE_BURST_BYTES = 8L * 1024 * 1024;
    private static final ObjectMapper CONTROL_JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final TerminalSessionService sessions;
    private final Executor writerExecutor;
    private final LongSupplier ticker;
    private final ConcurrentHashMap<String, Relay> relays = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private final Object overflowCloseExecutionLock = new Object();
    private String overflowSessionId;
    private PendingClose overflowPendingClose;
    private long lastEligibilityReconcileNanos;
    private String eligibilityReconcileCursor;

    public TerminalRelayCoordinator(TerminalSessionService sessions, Executor writerExecutor,
                                    LongSupplier ticker) {
        this(sessions, writerExecutor, ticker, task -> { });
    }

    public TerminalRelayCoordinator(TerminalSessionService sessions, Executor writerExecutor,
                                    LongSupplier ticker, PeriodicScheduler scheduler) {
        this.sessions = sessions;
        this.writerExecutor = writerExecutor;
        this.ticker = ticker;
        this.lastEligibilityReconcileNanos = ticker.getAsLong();
        scheduler.schedule(this::flushDueTraffic);
    }

    public boolean attach(String sessionId, TerminalPeer peer) {
        if (sessionId == null || peer == null || peer.session() == null) {
            return false;
        }
        Relay relay;
        boolean rejected = false;
        synchronized (lifecycleLock) {
            if (isOverflowSession(sessionId)) {
                relay = null;
                rejected = true;
            } else {
                relay = relays.get(sessionId);
            }
            if (!rejected && relay == null) {
                if (relays.size() >= MAX_RELAYS) {
                    rejected = true;
                } else {
                    relay = new Relay(sessionId, ticker.getAsLong());
                    relays.put(sessionId, relay);
                }
            }
            if (!rejected) {
                synchronized (relay) {
                if (relay.closed.get() || relay.peer(peer.role()) != null
                        || relay.reserved(peer.role())) {
                        rejected = true;
                    } else {
                        relay.setReserved(peer.role(), true);
                    }
                }
            }
        }
        if (rejected) {
            peer.close(policyStatus(CloseReason.SESSION_REJECTED));
            return false;
        }
        boolean eligible;
        try {
            eligible = sessions.isRelayAttachmentEligible(
                    sessionId, peer.role().name(), peer.agentId());
        } catch (RuntimeException exception) {
            eligible = false;
        }
        boolean activate = false;
        rejected = false;
        synchronized (lifecycleLock) {
            synchronized (relay) {
                relay.setReserved(peer.role(), false);
                if (!eligible || isOverflowSession(sessionId)
                        || relays.get(sessionId) != relay || relay.closed.get()
                        || relay.peer(peer.role()) != null) {
                    if (relay.browser == null && relay.agent == null && !relay.closed.get()) {
                        removeRelayAndMigrateUnderLock(sessionId, relay);
                    }
                    rejected = true;
                } else {
                    relay.setPeer(peer);
                    if (relay.browser != null && relay.agent != null && !relay.activating) {
                        relay.activating = true;
                        activate = true;
                    }
                }
            }
        }
        if (rejected) {
            peer.close(policyStatus(CloseReason.SESSION_REJECTED));
            return false;
        }
        if (!activate) {
            return true;
        }
        boolean activated;
        try {
            activated = sessions.markRelayActive(sessionId);
        } catch (RuntimeException exception) {
            activated = false;
        }
        synchronized (lifecycleLock) {
            synchronized (relay) {
                relay.activating = false;
                if (activated && !isOverflowSession(sessionId)
                        && relays.get(sessionId) == relay && !relay.closed.get()) {
                    relay.active = true;
                    return true;
                }
            }
        }
        if (!relay.closed.get()) {
            close(relay, CloseReason.SESSION_REJECTED);
        } else {
            try {
                peer.close(policyStatus(CloseReason.SESSION_REJECTED));
            } catch (RuntimeException ignored) {
                // Concurrent terminal close already owns teardown.
            }
        }
        return false;
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
        TrafficPersistence trafficPersistence = TrafficPersistence.SUCCESS;
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
                        relay.browserToAgentTotal += size;
                    } else {
                        relay.agentToBrowserTotal += size;
                    }
                    if (relay.unpersistedTraffic() >= TRAFFIC_FLUSH_BYTES) {
                        trafficPersistence = flushTraffic(relay, ticker.getAsLong());
                    }
                }
            }
        }
        if (scheduledPeer != null) {
            scheduledPeer.scheduleWriter(writerExecutor, () -> close(relay, CloseReason.IO_ERROR));
        }
        if (trafficPersistence != TrafficPersistence.SUCCESS) {
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
        while (true) {
            Relay relay;
            boolean overflowClose;
            synchronized (lifecycleLock) {
                relay = relays.get(sessionId);
                overflowClose = relay == null && relays.size() >= MAX_RELAYS;
                if (relay == null && !overflowClose) {
                    relay = new Relay(sessionId, ticker.getAsLong());
                    relay.closed.set(true);
                    relays.put(sessionId, relay);
                }
            }
            if (overflowClose) {
                closeOverflow(sessionId, persistenceClose);
                return;
            }
            synchronized (relay.closeExecutionLock) {
                TrafficBatch traffic;
                boolean pending;
                synchronized (lifecycleLock) {
                    if (relays.get(sessionId) != relay) {
                        continue;
                    }
                    pending = relay.pendingClose != null;
                    if (pending) {
                        traffic = null;
                    } else {
                        traffic = gateRelayClosed(relay);
                        relay.pendingClose = new PendingClose(persistenceClose, traffic);
                    }
                }
                if (pending) {
                    RuntimeException pendingFailure = attemptPendingClose(relay, false);
                    synchronized (lifecycleLock) {
                        if (relays.get(sessionId) == relay && relay.pendingClose != null) {
                            if (pendingFailure != null) {
                                throw pendingFailure;
                            }
                            throw new IllegalStateException("Terminal close persistence backlog");
                        }
                    }
                    continue;
                }
                closePeer(relay.browser, CloseReason.OPERATOR_CLOSED);
                closePeer(relay.agent, CloseReason.OPERATOR_CLOSED);
                RuntimeException failure = attemptPendingClose(relay, true);
                if (failure != null) {
                    throw failure;
                }
                return;
            }
        }
    }

    @Override
    public boolean closePersistedSessionIf(String sessionId, BooleanSupplier persistenceClose) {
        return closePersistedSessionConditionally(sessionId, () -> persistenceClose.getAsBoolean()
                ? ConditionalCloseDecision.PERSISTED : ConditionalCloseDecision.KEEP_OPEN)
                == ConditionalCloseResult.PERSISTED;
    }

    @Override
    public ConditionalCloseResult closePersistedSessionConditionally(
            String sessionId, Supplier<ConditionalCloseDecision> persistenceClose) {
        Relay relay = relays.get(sessionId);
        if (relay == null) {
            return result(persistenceClose.get());
        }
        synchronized (relay.closeExecutionLock) {
            boolean wasActive;
            TrafficBatch traffic;
            synchronized (relay) {
                if (relay.closed.get()) {
                    return ConditionalCloseResult.KEPT_OPEN;
                }
                wasActive = relay.active;
                traffic = gateRelayClosed(relay);
            }
            TrafficPersistence trafficResult = persistTraffic(traffic);
            if (trafficResult == TrafficPersistence.RETRY) {
                restoreRelay(relay, wasActive, traffic, false);
                return ConditionalCloseResult.KEPT_OPEN;
            }
            ConditionalCloseDecision decision;
            try {
                decision = persistenceClose.get();
            } catch (RuntimeException exception) {
                terminateLocalRelay(sessionId, relay);
                throw exception;
            }
            if (decision == ConditionalCloseDecision.KEEP_OPEN) {
                restoreRelay(relay, wasActive, traffic,
                        trafficResult == TrafficPersistence.SUCCESS);
                return ConditionalCloseResult.KEPT_OPEN;
            }
            terminateLocalRelay(sessionId, relay);
            return result(decision);
        }
    }

    private void restoreRelay(Relay relay, boolean wasActive, TrafficBatch traffic,
                              boolean trafficPersisted) {
        synchronized (relay) {
            relay.closed.set(false);
            relay.active = wasActive;
            if (traffic != null && trafficPersisted) {
                relay.browserToAgentAcknowledged = traffic.browserBytes;
                relay.agentToBrowserAcknowledged = traffic.agentBytes;
            }
        }
    }

    private ConditionalCloseResult result(ConditionalCloseDecision decision) {
        return decision == ConditionalCloseDecision.PERSISTED
                ? ConditionalCloseResult.PERSISTED
                : decision == ConditionalCloseDecision.LOCAL_ONLY
                ? ConditionalCloseResult.LOCAL_ONLY : ConditionalCloseResult.KEPT_OPEN;
    }

    private void terminateLocalRelay(String sessionId, Relay relay) {
        closePeer(relay.browser, CloseReason.SESSION_REJECTED);
        closePeer(relay.agent, CloseReason.SESSION_REJECTED);
        synchronized (lifecycleLock) {
            removeRelayAndMigrateUnderLock(sessionId, relay);
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
        synchronized (relay.closeExecutionLock) {
            synchronized (lifecycleLock) {
                if (relay.closed.get()) {
                    return;
                }
                TrafficBatch traffic = gateRelayClosed(relay);
                relay.pendingClose = new PendingClose(
                        () -> sessions.finishRelay(relay.sessionId, reason.operator, reason.name()),
                        traffic);
            }
            closePeer(relay.browser, reason);
            closePeer(relay.agent, reason);
            attemptPendingClose(relay, true);
        }
    }

    private TrafficBatch gateRelayClosed(Relay relay) {
        synchronized (relay) {
            relay.closed.set(true);
            relay.active = false;
            long browserBytes = relay.browserToAgentTotal;
            long agentBytes = relay.agentToBrowserTotal;
            relay.lastTrafficFlushNanos = ticker.getAsLong();
            return browserBytes == relay.browserToAgentAcknowledged
                    && agentBytes == relay.agentToBrowserAcknowledged
                    ? null : new TrafficBatch(relay.sessionId, browserBytes, agentBytes);
        }
    }

    private TrafficPersistence persistTraffic(TrafficBatch traffic) {
        if (traffic == null || traffic.browserBytes == 0 && traffic.agentBytes == 0) {
            return TrafficPersistence.SUCCESS;
        }
        try {
            return sessions.recordRelayTraffic(traffic.sessionId,
                    traffic.browserBytes, traffic.agentBytes)
                    ? TrafficPersistence.SUCCESS : TrafficPersistence.TERMINAL;
        } catch (RuntimeException exception) {
            return TrafficPersistence.RETRY;
        }
    }

    private TrafficPersistence flushTraffic(Relay relay, long now) {
        long browserBytes = relay.browserToAgentTotal;
        long agentBytes = relay.agentToBrowserTotal;
        if (browserBytes == relay.browserToAgentAcknowledged
                && agentBytes == relay.agentToBrowserAcknowledged) {
            return TrafficPersistence.SUCCESS;
        }
        try {
            if (!sessions.recordRelayTraffic(relay.sessionId, browserBytes, agentBytes)) {
                relay.browserToAgentAcknowledged = browserBytes;
                relay.agentToBrowserAcknowledged = agentBytes;
                return TrafficPersistence.TERMINAL;
            }
            relay.browserToAgentAcknowledged = browserBytes;
            relay.agentToBrowserAcknowledged = agentBytes;
            relay.lastTrafficFlushNanos = now;
            return TrafficPersistence.SUCCESS;
        } catch (RuntimeException exception) {
            return TrafficPersistence.RETRY;
        }
    }

    private void flushDueTraffic() {
        long now = ticker.getAsLong();
        for (Relay relay : relays.values()) {
            if (relay.closed.get()) {
                continue;
            }
            TrafficPersistence persistence = TrafficPersistence.SUCCESS;
            synchronized (relay) {
                if (relay.active && relay.unpersistedTraffic() > 0
                        && now - relay.lastTrafficFlushNanos >= TRAFFIC_FLUSH_NANOS) {
                    persistence = flushTraffic(relay, now);
                }
            }
            if (persistence != TrafficPersistence.SUCCESS) {
                close(relay, CloseReason.SESSION_REJECTED);
            }
        }
        int retries = 0;
        synchronized (lifecycleLock) {
            migrateOverflowUnderLock();
        }
        if (retryDueOverflow(now)) {
            retries++;
            attemptOverflowPending(false);
        }
        for (Relay relay : relays.values()) {
            if (retries >= MAX_CLOSE_RETRIES_PER_TICK) {
                break;
            }
            if (relay.closed.get() && retryDue(relay, now)) {
                retries++;
                attemptPendingClose(relay, false);
            }
        }
        reconcileLocalEligibility(now);
    }

    private void reconcileLocalEligibility(long now) {
        if (now - lastEligibilityReconcileNanos < ELIGIBILITY_RECONCILE_NANOS) {
            return;
        }
        lastEligibilityReconcileNanos = now;
        List<Relay> candidates = new ArrayList<>();
        for (Relay relay : relays.values()) {
            if (!relay.closed.get()) {
                candidates.add(relay);
            }
        }
        candidates.sort(Comparator.comparing(value -> value.sessionId));
        if (candidates.isEmpty()) {
            eligibilityReconcileCursor = null;
            return;
        }
        int start = 0;
        if (eligibilityReconcileCursor != null) {
            while (start < candidates.size()
                    && candidates.get(start).sessionId.compareTo(eligibilityReconcileCursor) <= 0) {
                start++;
            }
            if (start == candidates.size()) {
                start = 0;
            }
        }
        int count = Math.min(ELIGIBILITY_RECONCILE_BATCH, candidates.size());
        for (int index = 0; index < count; index++) {
            Relay relay = candidates.get((start + index) % candidates.size());
            eligibilityReconcileCursor = relay.sessionId;
            boolean open;
            try {
                open = sessions.isRelayStillOpen(relay.sessionId);
            } catch (RuntimeException exception) {
                continue;
            }
            if (!open) {
                synchronized (relay.closeExecutionLock) {
                    if (!relay.closed.get()) {
                        gateRelayClosed(relay);
                        terminateLocalRelay(relay.sessionId, relay);
                    }
                }
            }
        }
        if (candidates.size() <= ELIGIBILITY_RECONCILE_BATCH) {
            eligibilityReconcileCursor = null;
        }
    }

    private void closeOverflow(String sessionId, Runnable persistenceClose) {
        synchronized (overflowCloseExecutionLock) {
            synchronized (lifecycleLock) {
                if (overflowPendingClose != null) {
                    throw new IllegalStateException("Terminal close persistence backlog");
                }
                overflowSessionId = sessionId;
                overflowPendingClose = new PendingClose(persistenceClose, null);
            }
            RuntimeException failure = attemptOverflowPending(true);
            if (failure != null) {
                throw failure;
            }
        }
    }

    private boolean retryDueOverflow(long now) {
        synchronized (lifecycleLock) {
            return overflowPendingClose != null && !overflowPendingClose.inFlight
                    && now >= overflowPendingClose.nextRetryNanos;
        }
    }

    private RuntimeException attemptOverflowPending(boolean immediate) {
        synchronized (overflowCloseExecutionLock) {
            PendingClose pending;
            synchronized (lifecycleLock) {
                pending = overflowPendingClose;
                long now = ticker.getAsLong();
                if (pending == null || pending.inFlight
                        || !immediate && now < pending.nextRetryNanos) {
                    return null;
                }
                pending.inFlight = true;
            }
            RuntimeException failure = null;
            try {
                pending.action.run();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            synchronized (lifecycleLock) {
                if (overflowPendingClose != pending) {
                    return failure;
                }
                pending.inFlight = false;
                if (failure == null) {
                    overflowSessionId = null;
                    overflowPendingClose = null;
                } else {
                    pending.failures++;
                    pending.nextRetryNanos = ticker.getAsLong() + retryDelay(pending.failures);
                    migrateOverflowUnderLock();
                }
            }
            return failure;
        }
    }

    private boolean retryDue(Relay relay, long now) {
        synchronized (lifecycleLock) {
            PendingClose pending = relay.pendingClose;
            return relays.get(relay.sessionId) == relay && pending != null
                    && !pending.inFlight && now >= pending.nextRetryNanos;
        }
    }

    private RuntimeException attemptPendingClose(Relay relay, boolean immediate) {
        synchronized (relay.closeExecutionLock) {
            PendingClose pending;
            synchronized (lifecycleLock) {
                pending = relay.pendingClose;
                long now = ticker.getAsLong();
                if (pending == null || pending.inFlight || relays.get(relay.sessionId) != relay
                        || !immediate && now < pending.nextRetryNanos) {
                    return null;
                }
                pending.inFlight = true;
            }
            RuntimeException failure = null;
            if (pending.traffic != null) {
                TrafficPersistence persistence = persistTraffic(pending.traffic);
                if (persistence != TrafficPersistence.RETRY) {
                    pending.traffic = null;
                }
                if (persistence == TrafficPersistence.RETRY) {
                    failure = new IllegalStateException("Terminal traffic persistence failed");
                }
            }
            if (failure == null) {
                try {
                    pending.action.run();
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            }
            synchronized (lifecycleLock) {
                if (relay.pendingClose != pending || relays.get(relay.sessionId) != relay) {
                    return failure;
                }
                pending.inFlight = false;
                if (failure == null) {
                    relay.pendingClose = null;
                    removeRelayAndMigrateUnderLock(relay.sessionId, relay);
                } else {
                    pending.failures++;
                    pending.nextRetryNanos = ticker.getAsLong() + retryDelay(pending.failures);
                }
            }
            return failure;
        }
    }

    private long retryDelay(int failures) {
        long delay = CLOSE_RETRY_INITIAL_NANOS;
        for (int attempt = 1; attempt < failures && delay < CLOSE_RETRY_MAX_NANOS; attempt++) {
            delay = Math.min(CLOSE_RETRY_MAX_NANOS, delay * 2);
        }
        return delay;
    }

    private boolean isOverflowSession(String sessionId) {
        return overflowPendingClose != null && overflowSessionId.equals(sessionId);
    }

    private void removeRelayAndMigrateUnderLock(String sessionId, Relay relay) {
        if (relays.remove(sessionId, relay)) {
            migrateOverflowUnderLock();
        }
    }

    private void migrateOverflowUnderLock() {
        if (overflowPendingClose == null || overflowPendingClose.inFlight
                || relays.size() >= MAX_RELAYS || relays.containsKey(overflowSessionId)) {
            return;
        }
        Relay tombstone = new Relay(overflowSessionId, ticker.getAsLong());
        tombstone.closed.set(true);
        tombstone.pendingClose = overflowPendingClose;
        relays.put(overflowSessionId, tombstone);
        overflowSessionId = null;
        overflowPendingClose = null;
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

    private static final class TrafficBatch {
        private final String sessionId;
        private final long browserBytes;
        private final long agentBytes;

        private TrafficBatch(String sessionId, long browserBytes, long agentBytes) {
            this.sessionId = sessionId;
            this.browserBytes = browserBytes;
            this.agentBytes = agentBytes;
        }
    }

    private static final class PendingClose {
        private final Runnable action;
        private TrafficBatch traffic;
        private boolean inFlight;
        private int failures;
        private long nextRetryNanos;

        private PendingClose(Runnable action, TrafficBatch traffic) {
            this.action = action;
            this.traffic = traffic;
        }
    }

    private static final class Relay {
        private final String sessionId;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Object closeExecutionLock = new Object();
        private final TokenBucket browserRate;
        private final TokenBucket agentRate;
        private volatile TerminalPeer browser;
        private volatile TerminalPeer agent;
        private volatile boolean active;
        private boolean browserReserved;
        private boolean agentReserved;
        private boolean activating;
        private long browserToAgentTotal;
        private long agentToBrowserTotal;
        private long browserToAgentAcknowledged;
        private long agentToBrowserAcknowledged;
        private long lastTrafficFlushNanos;
        private PendingClose pendingClose;

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

        private boolean reserved(TerminalPeer.Role role) {
            return role == TerminalPeer.Role.BROWSER ? browserReserved : agentReserved;
        }

        private void setReserved(TerminalPeer.Role role, boolean reserved) {
            if (role == TerminalPeer.Role.BROWSER) {
                browserReserved = reserved;
            } else {
                agentReserved = reserved;
            }
        }

        private TokenBucket bucket(TerminalPeer.Role role) {
            return role == TerminalPeer.Role.BROWSER ? browserRate : agentRate;
        }

        private long unpersistedTraffic() {
            return browserToAgentTotal - browserToAgentAcknowledged
                    + agentToBrowserTotal - agentToBrowserAcknowledged;
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
