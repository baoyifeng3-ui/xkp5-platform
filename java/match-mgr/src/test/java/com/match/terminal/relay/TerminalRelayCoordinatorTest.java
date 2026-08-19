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
import java.util.concurrent.atomic.AtomicBoolean;

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
    public void onlyBrowserCanClaimOperatorClosed() {
        Fixture browserFixture = new Fixture();
        TerminalPeer browser = browserFixture.peer(TerminalPeer.Role.BROWSER);
        browserFixture.pair(browser, browserFixture.peer(TerminalPeer.Role.AGENT));

        browserFixture.coordinator.onText(browser,
                "{\"type\":\"close\",\"reason\":\"OPERATOR_CLOSED\"}");
        verify(browserFixture.sessions).finishRelay(SESSION_ID, true, "OPERATOR_CLOSED");

        Fixture agentFixture = new Fixture();
        TerminalPeer agent = agentFixture.peer(TerminalPeer.Role.AGENT);
        agentFixture.pair(agentFixture.peer(TerminalPeer.Role.BROWSER), agent);

        agentFixture.coordinator.onText(agent,
                "{\"type\":\"close\",\"reason\":\"OPERATOR_CLOSED\"}");
        verify(agentFixture.sessions).finishRelay(SESSION_ID, false, "PROTOCOL_ERROR");
    }

    @Test
    public void delayedAttachRejectsPersistedTerminalSessionWithoutCreatingRelay() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        AtomicBoolean terminal = new AtomicBoolean();
        when(sessions.isRelayAttachmentEligible(SESSION_ID, "BROWSER", null))
                .thenAnswer(invocation -> !terminal.get());

        coordinator.closePersistedSession(SESSION_ID, () -> terminal.set(true));

        assertFalse(coordinator.attach(SESSION_ID, browser));

        verify(browser.session()).close(any(CloseStatus.class));
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void terminalCloseRacingAttachCannotLeaveRelayOrSocket() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        CountDownLatch eligibilityEntered = new CountDownLatch(1);
        CountDownLatch releaseEligibility = new CountDownLatch(1);
        AtomicBoolean terminal = new AtomicBoolean();
        when(sessions.isRelayAttachmentEligible(SESSION_ID, "BROWSER", null))
                .thenAnswer(invocation -> {
                    eligibilityEntered.countDown();
                    assertTrue(releaseEligibility.await(5, TimeUnit.SECONDS));
                    return !terminal.get();
                });
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> attaching = callers.submit(() -> coordinator.attach(SESSION_ID, browser));
            assertTrue(eligibilityEntered.await(5, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(() -> coordinator.closePersistedSession(
                    SESSION_ID, () -> terminal.set(true)));
            releaseEligibility.countDown();
            attaching.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);

            assertTrue(terminal.get());
            verify(browser.session()).close(any(CloseStatus.class));
            assertEquals(0, coordinator.relayCount());
        } finally {
            releaseEligibility.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedTerminalPersistenceKeepsBoundedRejectionUntilSharedRetrySucceeds()
            throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        doThrow(new IllegalStateException("db unavailable")).doNothing()
                .when(sessions).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");

        coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        TerminalPeer delayed = peer(TerminalPeer.Role.BROWSER);

        assertFalse(coordinator.attach(SESSION_ID, delayed));
        assertEquals(1, coordinator.relayCount());
        verify(delayed.session()).close(any(CloseStatus.class));

        periodic.get().run();
        verify(sessions, times(1)).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();

        verify(sessions, times(2)).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void persistedCloseFlushesCountersBeforeStateChangeAndFailsSocketsClosed()
            throws Exception {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);
        fixture.pair(browser, agent);
        List<String> order = new ArrayList<>();
        when(fixture.sessions.recordRelayTraffic(SESSION_ID, 3L, 0L)).thenAnswer(invocation -> {
            order.add("traffic");
            return true;
        });
        fixture.coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1, 2, 3}));

        fixture.coordinator.closePersistedSession(SESSION_ID, () -> order.add("close"));

        assertEquals(Arrays.asList("traffic", "close"), order);
        verify(browser.session()).close(any(CloseStatus.class));
        verify(agent.session()).close(any(CloseStatus.class));
        assertEquals(0, fixture.coordinator.relayCount());
    }

    @Test
    public void throwingPersistedCloseStillClosesPeersAndRetriesWithBackoff() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        AtomicInteger attempts = new AtomicInteger();
        Runnable persistence = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("db unavailable");
            }
        };

        try {
            coordinator.closePersistedSession(SESSION_ID, persistence);
        } catch (IllegalStateException expected) {
            assertEquals("db unavailable", expected.getMessage());
        }

        verify(browser.session()).close(any(CloseStatus.class));
        verify(agent.session()).close(any(CloseStatus.class));
        assertEquals(1, coordinator.relayCount());
        periodic.get().run();
        assertEquals(1, attempts.get());
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();
        assertEquals(2, attempts.get());
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void flushAndSocketCloseFailuresDoNotSkipPersistedClose() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L))
                .thenThrow(new IllegalStateException("traffic unavailable")).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));
        doThrow(new IllegalStateException("socket close failed"))
                .when(browser.session()).close(any(CloseStatus.class));
        AtomicBoolean persisted = new AtomicBoolean();

        try {
            coordinator.closePersistedSession(SESSION_ID, () -> persisted.set(true));
            throw new AssertionError("traffic persistence failure was hidden");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal traffic persistence failed", expected.getMessage());
        }

        assertFalse(persisted.get());
        verify(browser.session()).close(any(CloseStatus.class));
        verify(agent.session()).close(any(CloseStatus.class));
        assertEquals(1, coordinator.relayCount());
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();
        assertTrue(persisted.get());
        verify(sessions, times(2)).recordRelayTraffic(SESSION_ID, 1L, 0L);
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void laterCloseCannotReplacePendingTrafficAndCloseIntent() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L))
                .thenThrow(new IllegalStateException("traffic unavailable")).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));
        AtomicBoolean firstClose = new AtomicBoolean();
        AtomicBoolean replacementClose = new AtomicBoolean();

        try {
            coordinator.closePersistedSession(SESSION_ID, () -> firstClose.set(true));
            throw new AssertionError("traffic failure was hidden");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal traffic persistence failed", expected.getMessage());
        }
        try {
            coordinator.closePersistedSession(SESSION_ID, () -> replacementClose.set(true));
            throw new AssertionError("pending close was replaced");
        } catch (IllegalStateException expected) {
            assertEquals("Terminal close persistence backlog", expected.getMessage());
        }

        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();
        assertTrue(firstClose.get());
        assertFalse(replacementClose.get());
        verify(sessions, times(2)).recordRelayTraffic(SESSION_ID, 1L, 0L);
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void failedPeriodicTrafficFlushRetainsBytesForCloseSequence() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L))
                .thenThrow(new IllegalStateException("traffic unavailable")).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();

        verify(sessions, times(2)).recordRelayTraffic(SESSION_ID, 1L, 0L);
        verify(sessions).finishRelay(SESSION_ID, false, "SESSION_REJECTED");
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void slowEligibilityReadDoesNotBlockTerminalCloseGate() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        CountDownLatch eligibilityEntered = new CountDownLatch(1);
        CountDownLatch releaseEligibility = new CountDownLatch(1);
        when(sessions.isRelayAttachmentEligible(SESSION_ID, "BROWSER", null))
                .thenAnswer(invocation -> {
                    eligibilityEntered.countDown();
                    assertTrue(releaseEligibility.await(5, TimeUnit.SECONDS));
                    return true;
                });
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> attach = callers.submit(() -> coordinator.attach(SESSION_ID, browser));
            assertTrue(eligibilityEntered.await(5, TimeUnit.SECONDS));
            AtomicBoolean persisted = new AtomicBoolean();
            Future<?> close = callers.submit(() -> coordinator.closePersistedSession(
                    SESSION_ID, () -> persisted.set(true)));
            close.get(2, TimeUnit.SECONDS);
            assertTrue(persisted.get());
            releaseEligibility.countDown();
            assertFalse(attach.get(5, TimeUnit.SECONDS));
        } finally {
            releaseEligibility.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void concurrentPersistedCloseCallbacksSerializeAndReportOwnCompletion() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = callers.submit(() -> coordinator.closePersistedSession(SESSION_ID, () -> {
                firstEntered.countDown();
                try {
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                completed.incrementAndGet();
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<?> second = callers.submit(() -> coordinator.closePersistedSession(
                    SESSION_ID, completed::incrementAndGet));
            try {
                second.get(100, TimeUnit.MILLISECONDS);
                throw new AssertionError("second close completed before its callback");
            } catch (java.util.concurrent.TimeoutException expected) {
                // The first callback owns the per-relay close execution slot.
            }
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(2, completed.get());
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void retryTickIsBudgetedAndDoesNotStarveActiveTrafficFlush() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        doThrow(new IllegalStateException("db unavailable"))
                .when(sessions).finishRelay(any(String.class), eq(false), eq("PEER_DISCONNECTED"));
        for (int i = 0; i < 10; i++) {
            String sessionId = "retry-" + i;
            TerminalPeer peer = peer(TerminalPeer.Role.BROWSER);
            assertTrue(coordinator.attach(sessionId, peer));
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        }
        String activeId = "active-session";
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(activeId)).thenReturn(true);
        when(sessions.recordRelayTraffic(activeId, 1L, 0L)).thenReturn(true);
        assertTrue(coordinator.attach(activeId, browser));
        assertTrue(coordinator.attach(activeId, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();

        verify(sessions).recordRelayTraffic(activeId, 1L, 0L);
        verify(sessions, times(10 + TerminalRelayCoordinator.MAX_CLOSE_RETRIES_PER_TICK))
                .finishRelay(any(String.class), eq(false), eq("PEER_DISCONNECTED"));
        periodic.get().run();
        verify(sessions, times(20))
                .finishRelay(any(String.class), eq(false), eq("PEER_DISCONNECTED"));
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
        allowAttachments(sessions);
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
        allowAttachments(sessions);
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
        allowAttachments(sessions);
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
    public void trafficSnapshotsAreAbsoluteAndIncreaseMonotonically() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 3L, 0L)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 5L, 0L)).thenReturn(true);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));

        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1, 2, 3}));
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{4, 5}));
        coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);

        verify(sessions).recordRelayTraffic(SESSION_ID, 3L, 0L);
        verify(sessions).recordRelayTraffic(SESSION_ID, 5L, 0L);
        verify(sessions, never()).recordRelayTraffic(SESSION_ID, 2L, 0L);
    }

    @Test
    public void terminalTrafficWriteResultDoesNotStrandCloseTombstone() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, () -> 0L);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L)).thenReturn(false);
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);

        verify(sessions).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void cleanupCompletesWhenTrafficAndSocketCleanupThrow() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = peer(TerminalPeer.Role.AGENT);
        when(sessions.markRelayActive(SESSION_ID)).thenReturn(true);
        when(sessions.recordRelayTraffic(SESSION_ID, 1L, 0L))
                .thenThrow(new IllegalStateException("db unavailable")).thenReturn(true);
        doThrow(new IllegalStateException("socket close failed"))
                .when(browser.session()).close(any(CloseStatus.class));
        assertTrue(coordinator.attach(SESSION_ID, browser));
        assertTrue(coordinator.attach(SESSION_ID, agent));
        coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{1}));

        coordinator.detach(browser, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);

        verify(browser.session()).close(any(CloseStatus.class));
        verify(agent.session()).close(any(CloseStatus.class));
        verify(sessions, never()).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        assertEquals(1, coordinator.relayCount());

        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();

        verify(sessions).finishRelay(SESSION_ID, false, "PEER_DISCONNECTED");
        verify(sessions, times(2)).recordRelayTraffic(SESSION_ID, 1L, 0L);
        assertEquals(0, coordinator.relayCount());
    }

    @Test
    public void fullMapCloseFailureRetriesAndDoesNotCausePermanentGlobalRejection() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
        AtomicLong ticker = new AtomicLong();
        AtomicReference<Runnable> periodic = new AtomicReference<>();
        TerminalRelayCoordinator coordinator = new TerminalRelayCoordinator(
                sessions, Runnable::run, ticker::get, periodic::set);
        TerminalPeer first = null;
        TerminalPeer second = null;
        for (int i = 0; i < TerminalRelayCoordinator.MAX_RELAYS; i++) {
            TerminalPeer browser = peer(TerminalPeer.Role.BROWSER);
            if (i == 0) {
                first = browser;
            } else if (i == 1) {
                second = browser;
            }
            assertTrue(coordinator.attach(String.format("%08d-3333-4333-8333-333333333333", i),
                    browser));
        }
        AtomicInteger attempts = new AtomicInteger();
        Runnable close = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("db unavailable");
            }
        };

        try {
            coordinator.closePersistedSession("overflow-session", close);
            throw new AssertionError("close failure was hidden");
        } catch (IllegalStateException expected) {
            assertEquals("db unavailable", expected.getMessage());
        }
        String firstSession = "00000000-3333-4333-8333-333333333333";
        when(sessions.markRelayActive(firstSession)).thenReturn(true);
        assertTrue(coordinator.attach(firstSession, peer(TerminalPeer.Role.AGENT)));
        assertFalse(coordinator.attach("overflow-session", peer(TerminalPeer.Role.BROWSER)));
        assertFalse(coordinator.attach("capacity-still-full", peer(TerminalPeer.Role.BROWSER)));

        coordinator.detach(second, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        assertEquals(TerminalRelayCoordinator.MAX_RELAYS, coordinator.relayCount());
        assertFalse(coordinator.attach("overflow-session", peer(TerminalPeer.Role.BROWSER)));

        coordinator.detach(first, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        assertTrue(coordinator.attach("unrelated-after-slot-freed",
                peer(TerminalPeer.Role.BROWSER)));
        ticker.set(TimeUnit.SECONDS.toNanos(1));
        periodic.get().run();
        assertEquals(2, attempts.get());
        assertEquals(TerminalRelayCoordinator.MAX_RELAYS - 1, coordinator.relayCount());
    }

    @Test
    public void dataCannotCrossUntilActivationPersistenceCompletes() throws Exception {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        allowAttachments(sessions);
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
        allowAttachments(sessions);
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
        allowAttachments(sessions);
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

    @Test
    public void conditionalCloseThatLosesPersistenceRaceKeepsPeersAttached() {
        Fixture fixture = new Fixture();
        TerminalPeer browser = fixture.peer(TerminalPeer.Role.BROWSER);
        TerminalPeer agent = fixture.peer(TerminalPeer.Role.AGENT);
        fixture.pair(browser, agent);
        fixture.coordinator.onBinary(browser, ByteBuffer.wrap(new byte[]{7}));

        assertFalse(fixture.coordinator.closePersistedSessionIf(SESSION_ID, () -> false));

        assertEquals(1, fixture.coordinator.relayCount());
        assertTrue(browser.session().isOpen());
        assertTrue(agent.session().isOpen());
        verify(fixture.sessions).recordRelayTraffic(SESSION_ID, 1L, 0L);
    }

    private TerminalPeer peer(TerminalPeer.Role role) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        return new TerminalPeer(role, role == TerminalPeer.Role.AGENT ? "agent-1" : null, socket);
    }

    private static void allowAttachments(TerminalSessionService sessions) {
        when(sessions.isRelayAttachmentEligible(any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(true);
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
            allowAttachments(sessions);
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
