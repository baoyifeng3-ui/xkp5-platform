package com.match.terminal.relay;

import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalRelayCoordinatorTest {
    private static final String SESSION_ID = "33333333-3333-4333-8333-333333333333";

    @Test
    public void activatesOnlyAfterOnePeerOfEachRoleAndRejectsDuplicates() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);

        assertTrue(fixture.coordinator.attach(SESSION_ID, browser));
        verify(fixture.sessions, never()).markRelayActive(any(String.class));
        assertFalse(fixture.coordinator.attach(SESSION_ID,
                fixture.peer(TerminalPeer.Role.BROWSER)));
        assertTrue(fixture.coordinator.attach(SESSION_ID, agent));

        verify(fixture.sessions).markRelayActive(SESSION_ID);
        assertEquals(1, fixture.coordinator.relayCount());
    }

    @Test
    public void forwardsOpaqueBinaryBothDirectionsAndPersistsOnlyCounts() throws Exception {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);
        fixture.pair(browser, agent);
        byte[] first = new byte[]{0, 1, (byte) 0xff, 10};
        byte[] second = new byte[]{9, 8, 7};

        fixture.coordinator.onBinary(browser, ByteBuffer.wrap(first));
        fixture.coordinator.onBinary(agent, ByteBuffer.wrap(second));

        assertArrayEquals(first, fixture.binaryAt(agent, 0));
        assertArrayEquals(second, fixture.binaryAt(browser, 0));
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class),
                eq(4L), eq(0L));
        fixture.coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        verify(fixture.sessions).recordRelayTraffic(SESSION_ID, 4L, 3L);
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
    }

    @Test
    public void eitherPeerCloseClosesBothOnceAndRemovesRelay() throws Exception {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);
        fixture.pair(browser, agent);

        fixture.coordinator.detach(agent, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        fixture.coordinator.detach(agent, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);

        verify(browser.session(), times(1)).close(any(CloseStatus.class));
        verify(agent.session(), times(1)).close(any(CloseStatus.class));
        verify(fixture.sessions, times(1)).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        assertEquals(0, fixture.coordinator.relayCount());
    }

    @Test
    public void acceptsClosedControlSchemaAndRejectsMalformedOrOversizedFrames() throws Exception {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);
        fixture.pair(browser, agent);

        fixture.coordinator.onText(browser, "{\"type\":\"resize\",\"columns\":120,\"rows\":36}");
        fixture.coordinator.onText(browser, "{\"type\":\"ping\"}");
        assertEquals("{\"type\":\"resize\",\"columns\":120,\"rows\":36}",
                fixture.textAt(agent, 0));
        assertEquals("{\"type\":\"ping\"}", fixture.textAt(agent, 1));
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class), any(Long.class), any(Long.class));

        fixture.coordinator.onText(browser, "{\"type\":\"ping\",\"extra\":true}");
        assertEquals(0, fixture.coordinator.relayCount());
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "PROTOCOL_ERROR");
    }

    @Test
    public void rejectsEveryInvalidControlShape() {
        for (String invalid : Arrays.asList(
                "{\"type\":\"resize\",\"columns\":19,\"rows\":36}",
                "{\"type\":\"resize\",\"columns\":120,\"rows\":201}",
                "{\"type\":\"ping\",\"type\":\"pong\"}",
                "{\"type\":\"unknown\"}",
                "{\"type\":\"close\",\"reason\":\"arbitrary\"}",
                "{\"type\":\"pong\"} trailing",
                "not-json")) {
            Fixture fixture = new Fixture();
            TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
            fixture.pair(browser, fixture.peer(TerminalPeer.Role.AGENT));
            fixture.coordinator.onText(browser, invalid);
            assertEquals(invalid, 0, fixture.coordinator.relayCount());
        }
    }

    @Test
    public void tokenBucketAllowsEightMiBBurstThenFailsWithoutBlocking() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        fixture.pair(browser, fixture.peer(TerminalPeer.Role.AGENT));
        byte[] frame = new byte[64 * 1024];

        for (int i = 0; i < 128; i++) {
            fixture.coordinator.onBinary(browser, ByteBuffer.wrap(frame));
        }
        assertEquals(1, fixture.coordinator.relayCount());
        fixture.coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        assertEquals(0, fixture.coordinator.relayCount());
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "RATE_LIMITED");
    }

    @Test
    public void tokenBucketRefillsAtTwoMiBPerSecondUsingInjectedTicker() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        fixture.pair(browser, fixture.peer(TerminalPeer.Role.AGENT));
        byte[] frame = new byte[64 * 1024];
        for (int i = 0; i < 128; i++) {
            fixture.coordinator.onBinary(browser, ByteBuffer.wrap(frame));
        }
        fixture.ticker.addAndGet(1_000_000_000L);
        for (int i = 0; i < 32; i++) {
            fixture.coordinator.onBinary(browser, ByteBuffer.wrap(frame));
        }
        assertEquals(1, fixture.coordinator.relayCount());
    }

    @Test
    public void boundsRelayMapAndEachOutboundQueue() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        List<Runnable> writes = new CopyOnWriteArrayList<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, writes::add, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        assertTrue(coordinator.attach("00000000-3333-4333-8333-333333333333", browser));
        for (int i = 1; i < TerminalRelayCoordinator.MAX_RELAYS; i++) {
            assertTrue(coordinator.attach(String.format("%08d-3333-4333-8333-333333333333", i),
                    peer(TerminalPeer.Role.BROWSER)));
        }
        TerminalPeer rejected = peer(TerminalPeer.Role.BROWSER);
        assertFalse(coordinator.attach("overflow-session", rejected));
        assertEquals(TerminalRelayCoordinator.MAX_RELAYS, coordinator.relayCount());

        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        String sessionId = "00000000-3333-4333-8333-333333333333";
        when(sessions.markRelayActive(sessionId)).thenReturn(true);
        when(sessions.recordRelayTraffic(eq(sessionId), anyLong(), anyLong()))
                .thenReturn(true);
        assertTrue(coordinator.attach(sessionId, agent));
        byte[] frame = new byte[64 * 1024];
        for (int i = 0; i < 16; i++) {
            coordinator.onBinary(browser, ByteBuffer.wrap(frame));
        }
        assertEquals(1, writes.size());
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));
        verify(sessions).finishRelay(sessionId, false, "BACKPRESSURE");
    }

    @Test
    public void zeroLengthFramesCannotCreateAnUnboundedMessageQueue() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        List<Runnable> writes = new CopyOnWriteArrayList<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, writes::add, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));

        for (int i = 0; i < TerminalPeer.MAX_QUEUED_MESSAGES; i++) {
            coordinator.onBinary(browser, ByteBuffer.allocate(0));
        }
        assertEquals(1, coordinator.relayCount());
        coordinator.onBinary(browser, ByteBuffer.allocate(0));

        assertEquals(0, coordinator.relayCount());
        verify(sessions).finishRelay(SESSION_ID, false, "BACKPRESSURE");
    }

    @Test
    public void lowVolumeBinaryTrafficFlushesOnTheSharedTimeBoundary() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 3L, 0L)).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));

        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1, 2, 3}));
        verify(sessions, never()).recordRelayTraffic(any(String.class), anyLong(), anyLong());
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();

        verify(sessions).recordRelayTraffic(SESSION_ID, 3L, 0L);
        assertEquals(1, coordinator.relayCount());
    }

    @Test
    public void cleanupCompletesWhenTrafficAndSocketCleanupThrow() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L))
                .thenThrow(new IllegalStateException("db unavailable"));
        doThrow(new IllegalStateException("socket close failed"))
                .when(browser.session()).close(any(CloseStatus.class));
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);

        verify(browser.session()).close(any(CloseStatus.class));
        verify(agent.session()).close(any(CloseStatus.class));
        verify(sessions).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void dataCannotCrossUntilActivationPersistenceCompletes() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        CountDownLatch activationEntered = new CountDownLatch(1);
        CountDownLatch releaseActivation = new CountDownLatch(1);
        when(sessions.markRelayActive(SESSION_ID)).thenAnswer(invocation -> {
            activationEntered.countDown();
            assertTrue(releaseActivation.await(5, TimeUnit.SECONDS));
            return true;
        });
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        ExecutorService attaching = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> attached = attaching.submit(() -> coordinator.attach(SESSION_ID, agent));
            assertTrue(activationEntered.await(5, TimeUnit.SECONDS));

            coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{7}));
            verify(agent.session(), never()).sendMessage(any(WebSocketMessage.class));
            releaseActivation.countDown();
            assertTrue(attached.get(5, TimeUnit.SECONDS));
            coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{8}));
            verify(agent.session(), times(1)).sendMessage(any(WebSocketMessage.class));
            coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
            verify(sessions).recordRelayTraffic(SESSION_ID, 1L, 0L);
        } finally {
            releaseActivation.countDown();
            attaching.shutdownNow();
            assertTrue(attaching.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void activationFailureNeverOpensTheForwardingGate() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(false);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        assertTrue(coordinator.attach(SESSION_ID, browser));

        assertFalse(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        verify(agent.session(), never()).sendMessage(any(WebSocketMessage.class));
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void preActivationControlsAreValidatedBeforeTheyAreDropped() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        assertTrue(fixture.coordinator.attach(SESSION_ID, browser));

        fixture.coordinator.onText(browser, "{\"type\":\"ping\"}");
        assertEquals(1, fixture.coordinator.relayCount());
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class), anyLong(), anyLong());

        fixture.coordinator.onText(browser, "{\"type\":\"ping\",\"extra\":true}");

        assertEquals(0, fixture.coordinator.relayCount());
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "PROTOCOL_ERROR");
    }

    @Test
    public void preActivationBinaryConsumesRateBudgetWithoutForwardingOrTrafficAccounting() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        assertTrue(fixture.coordinator.attach(SESSION_ID, browser));
        byte[] frame = new byte[TerminalRelayCoordinator.MAX_BINARY_BYTES];

        for (int i = 0; i < 128; i++) {
            fixture.coordinator.onBinary(browser, ByteBuffer.wrap(frame));
        }
        assertEquals(1, fixture.coordinator.relayCount());
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class), anyLong(), anyLong());

        fixture.coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        assertEquals(0, fixture.coordinator.relayCount());
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "RATE_LIMITED");
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class), anyLong(), anyLong());
    }

    @Test
    public void preActivationOversizedBinaryClosesAsAProtocolViolation() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        assertTrue(fixture.coordinator.attach(SESSION_ID, browser));

        fixture.coordinator.onBinary(browser,
                ByteBuffer.wrap(new byte[TerminalRelayCoordinator.MAX_BINARY_BYTES + 1]));

        assertEquals(0, fixture.coordinator.relayCount());
        verify(fixture.sessions).finishRelay(SESSION_ID, false, "PROTOCOL_ERROR");
        verify(fixture.sessions, never()).recordRelayTraffic(any(String.class), anyLong(), anyLong());
    }

    @Test
    public void acceptedBinaryIsAccountedWhenDetachOverlapsWriterScheduling() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        CountDownLatch schedulingEntered = new CountDownLatch(1);
        CountDownLatch releaseScheduling = new CountDownLatch(1);
        Executor blockedScheduler = command -> {
            schedulingEntered.countDown();
            try {
                assertTrue(releaseScheduling.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        };
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, blockedScheduler, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L)).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> forwarding = caller.submit(
                    () -> coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{7})));
            assertTrue(schedulingEntered.await(5, TimeUnit.SECONDS));

            coordinator.detach(agent, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
            releaseScheduling.countDown();
            forwarding.get(5, TimeUnit.SECONDS);

            verify(sessions, times(1)).recordRelayTraffic(SESSION_ID, 1L, 0L);
            verify(sessions, times(1)).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
            assertEquals(0, coordinator.relayCount());
        } finally {
            releaseScheduling.countDown();
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void outboundWriterRemainsSerializedOnAMultithreadedExecutor() throws Exception {
        ExecutorService writers = Executors.newFixedThreadPool(2);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch bothCompleted = new CountDownLatch(2);
        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            if (completed.get() == 0) {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
            }
            completed.incrementAndGet();
            active.decrementAndGet();
            bothCompleted.countDown();
            return null;
        }).when(socket).sendMessage(any(WebSocketMessage.class));
        TerminalPeer peer = new TerminalPeer(TerminalPeer.Role.AGENT, "agent-1", socket);
        try {
            assertTrue(peer.enqueue(new BinaryMessage(new byte[]{1}), writers, () -> { }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            assertTrue(peer.enqueue(new BinaryMessage(new byte[]{2}), writers, () -> { }));
            releaseFirst.countDown();
            assertTrue(bothCompleted.await(5, TimeUnit.SECONDS));
            assertEquals(1, maximum.get());
        } finally {
            releaseFirst.countDown();
            peer.close(CloseStatus.NORMAL);
            writers.shutdownNow();
            assertTrue(writers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private TerminalPeer peer(TerminalPeer.Role role) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        return new TerminalPeer(role, role == TerminalPeer.Role.AGENT ? "agent-1" : null, socket);
    }

    private static final class Fixture {
        private final TerminalSessionService sessions = mock(TerminalSessionService.class);
        private final AtomicLong ticker = new AtomicLong();
        private final Executor direct = Runnable::run;
        private final TerminalRelayCoordinator coordinator =
                new TerminalRelayCoordinator(sessions, direct, ticker::get);
        private final List<WebSocketMessage<?>> browserMessages = new ArrayList<>();
        private final List<WebSocketMessage<?>> agentMessages = new ArrayList<>();

        private Fixture() {
            when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
            when(sessions.recordRelayTraffic(any(String.class), anyLong(), anyLong()))
                    .thenReturn(true);
        }

        private TerminalPeer peer(TerminalPeer.Role role) {
            WebSocketSession socket = mock(WebSocketSession.class);
            when(socket.isOpen()).thenReturn(true);
            try {
                org.mockito.Mockito.doAnswer(invocation -> {
                    (role == TerminalPeer.Role.BROWSER ? browserMessages : agentMessages)
                            .add(invocation.getArgument(0));
                    return null;
                }).when(socket).sendMessage(any(WebSocketMessage.class));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            return new TerminalPeer(role, null, socket);
        }

        private void pair(TerminalPeer browser, TerminalPeer agent) {
            assertTrue(coordinator.attach(SESSION_ID, browser));
            assertTrue(coordinator.attach(SESSION_ID, agent));
        }

        private byte[] binaryAt(TerminalPeer peer, int index) {
            BinaryMessage message = (BinaryMessage) (peer.role() == TerminalPeer.Role.AGENT
                    ? agentMessages : browserMessages).get(index);
            ByteBuffer payload = message.getPayload().slice();
            byte[] value = new byte[payload.remaining()];
            payload.get(value);
            return value;
        }

        private String textAt(TerminalPeer peer, int index) {
            return ((TextMessage) (peer.role() == TerminalPeer.Role.AGENT
                    ? agentMessages : browserMessages).get(index)).getPayload();
        }
    }
}
