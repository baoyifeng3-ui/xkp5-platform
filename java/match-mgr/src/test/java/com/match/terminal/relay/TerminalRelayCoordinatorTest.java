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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
