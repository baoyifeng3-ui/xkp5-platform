package com.match.terminal.relay;

import org.junit.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalWebSocketHandlerTest {
    private static final String SESSION_ID = "33333333-3333-4333-8333-333333333333";

    @Test
    public void bindsAuthenticatedPeerAndAdvertisesOnlySafeProtocol() throws Exception {
        TerminalRelayCoordinator coordinator = mock(TerminalRelayCoordinator.class);
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler(coordinator);
        TerminalPeer peer = new TerminalPeer(TerminalPeer.Role.BROWSER, null);
        WebSocketSession socket = socket(peer);
        when(coordinator.attach(SESSION_ID, peer)).thenReturn(true);

        handler.afterConnectionEstablished(socket);

        assertEquals(socket, peer.session());
        assertEquals(Collections.singletonList(TerminalHandshakeInterceptor.SAFE_SUBPROTOCOL),
                handler.getSubProtocols());
        assertFalse(handler.supportsPartialMessages());
        verify(coordinator).attach(SESSION_ID, peer);
    }

    @Test
    public void delegatesCompleteFramesAndRejectsFragmentedOrOversizedFrames() throws Exception {
        TerminalRelayCoordinator coordinator = mock(TerminalRelayCoordinator.class);
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler(coordinator);
        TerminalPeer peer = new TerminalPeer(TerminalPeer.Role.AGENT, "agent-1");
        WebSocketSession socket = socket(peer);
        peer.bind(socket);

        handler.handleMessage(socket, new BinaryMessage(new byte[]{1, 2, 3}));
        handler.handleMessage(socket, new TextMessage("{\"type\":\"ping\"}"));
        verify(coordinator).onBinary(eq(peer), any(java.nio.ByteBuffer.class));
        verify(coordinator).onText(peer, "{\"type\":\"ping\"}");

        handler.handleMessage(socket, new BinaryMessage(new byte[]{1}, false));
        verify(coordinator).detach(peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);

        TerminalRelayCoordinator oversizedCoordinator = mock(TerminalRelayCoordinator.class);
        TerminalWebSocketHandler oversized = new TerminalWebSocketHandler(oversizedCoordinator);
        oversized.handleMessage(socket,
                new BinaryMessage(new byte[TerminalRelayCoordinator.MAX_BINARY_BYTES + 1]));
        verify(oversizedCoordinator).detach(peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);
        verify(oversizedCoordinator, never()).onBinary(any(TerminalPeer.class),
                any(java.nio.ByteBuffer.class));

        TerminalRelayCoordinator oversizedTextCoordinator = mock(TerminalRelayCoordinator.class);
        TerminalWebSocketHandler oversizedText = new TerminalWebSocketHandler(oversizedTextCoordinator);
        oversizedText.handleMessage(socket,
                new TextMessage(new String(new char[TerminalRelayCoordinator.MAX_TEXT_BYTES + 1])
                        .replace('\0', 'x')));
        verify(oversizedTextCoordinator).detach(
                peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);
        verify(oversizedTextCoordinator, never()).onText(any(TerminalPeer.class), any(String.class));
    }

    @Test
    public void missingHandshakeAttributesFailClosedWithoutCredentialsInReason() throws Exception {
        TerminalRelayCoordinator coordinator = mock(TerminalRelayCoordinator.class);
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler(coordinator);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(Collections.emptyMap());
        when(socket.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(socket);

        verify(socket).close(any(CloseStatus.class));
        verify(coordinator, never()).attach(any(String.class), any(TerminalPeer.class));
    }

    @Test
    public void protocolNegotiationSelectsOnlyTheStableNonSecretProtocol() {
        TerminalWebSocketHandler handler = new TerminalWebSocketHandler(
                mock(TerminalRelayCoordinator.class));
        String ticketProtocol = "xkp-terminal-ticket.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO12";

        String selected = new ExposedHandshakeHandler().select(
                Arrays.asList(TerminalHandshakeInterceptor.SAFE_SUBPROTOCOL, ticketProtocol),
                handler);

        assertEquals(TerminalHandshakeInterceptor.SAFE_SUBPROTOCOL, selected);
        assertFalse(ticketProtocol.equals(selected));
    }

    private WebSocketSession socket(TerminalPeer peer) {
        WebSocketSession socket = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TerminalHandshakeInterceptor.SESSION_ID_ATTRIBUTE, SESSION_ID);
        attributes.put(TerminalHandshakeInterceptor.PEER_ATTRIBUTE, peer);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private static final class ExposedHandshakeHandler extends DefaultHandshakeHandler {
        private String select(List<String> requested, WebSocketHandler handler) {
            return selectProtocol(requested, handler);
        }
    }
}
