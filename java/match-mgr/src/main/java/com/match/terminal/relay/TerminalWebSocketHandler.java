package com.match.terminal.relay;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TerminalWebSocketHandler extends AbstractWebSocketHandler implements SubProtocolCapable {
    private static final CloseStatus HANDSHAKE_REJECTED =
            new CloseStatus(1008, "HANDSHAKE_REJECTED");

    private final TerminalRelayCoordinator coordinator;

    public TerminalWebSocketHandler(TerminalRelayCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Object sessionId = attributes.get(TerminalHandshakeInterceptor.SESSION_ID_ATTRIBUTE);
        Object peerValue = attributes.get(TerminalHandshakeInterceptor.PEER_ATTRIBUTE);
        if (!(sessionId instanceof String) || !(peerValue instanceof TerminalPeer)) {
            session.close(HANDSHAKE_REJECTED);
            return;
        }
        TerminalPeer peer = (TerminalPeer) peerValue;
        try {
            peer.bind(session);
            if (!coordinator.attach((String) sessionId, peer)) {
                peer.close(HANDSHAKE_REJECTED);
            }
        } catch (RuntimeException exception) {
            peer.close(HANDSHAKE_REJECTED);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        TerminalPeer peer = peer(session);
        if (peer == null) {
            return;
        }
        if (!message.isLast() || message.getPayloadLength() > TerminalRelayCoordinator.MAX_BINARY_BYTES) {
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);
            return;
        }
        coordinator.onBinary(peer, message.getPayload().slice());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        TerminalPeer peer = peer(session);
        if (peer == null) {
            return;
        }
        if (!message.isLast() || message.getPayloadLength() > TerminalRelayCoordinator.MAX_TEXT_BYTES) {
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);
            return;
        }
        coordinator.onText(peer, message.getPayload());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        TerminalPeer peer = peer(session);
        if (peer != null) {
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.PROTOCOL_ERROR);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        TerminalPeer peer = peer(session);
        if (peer != null) {
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.IO_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalPeer peer = peer(session);
        if (peer != null) {
            coordinator.detach(peer, TerminalRelayCoordinator.CloseReason.PEER_DISCONNECTED);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList(TerminalHandshakeInterceptor.SAFE_SUBPROTOCOL);
    }

    private TerminalPeer peer(WebSocketSession session) {
        Object value = session.getAttributes().get(TerminalHandshakeInterceptor.PEER_ATTRIBUTE);
        return value instanceof TerminalPeer ? (TerminalPeer) value : null;
    }
}
