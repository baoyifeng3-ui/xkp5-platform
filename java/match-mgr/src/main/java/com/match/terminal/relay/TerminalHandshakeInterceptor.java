package com.match.terminal.relay;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.terminal.service.TerminalSessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class TerminalHandshakeInterceptor implements HandshakeInterceptor {
    public static final String SESSION_ID_ATTRIBUTE = "terminalSessionId";
    public static final String PEER_ATTRIBUTE = "terminalPeer";
    public static final String AGENT_TICKET_HEADER = "X-Terminal-Ticket";
    public static final String WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SAFE_SUBPROTOCOL = "xkp-terminal-v1";

    private static final String TICKET_PREFIX = "xkp-terminal-ticket.";
    private static final Pattern TICKET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern SESSION = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

    private final TerminalPeer.Role role;
    private final AgentCredentialService credentials;
    private final TerminalSessionService sessions;
    private final Set<String> browserOrigins;

    private TerminalHandshakeInterceptor(TerminalPeer.Role role,
                                         AgentCredentialService credentials,
                                         TerminalSessionService sessions,
                                         String[] browserOrigins) {
        this.role = role;
        this.credentials = credentials;
        this.sessions = sessions;
        this.browserOrigins = validateOrigins(browserOrigins);
    }

    public static TerminalHandshakeInterceptor browser(TerminalSessionService sessions,
                                                       String[] allowedOrigins) {
        return new TerminalHandshakeInterceptor(TerminalPeer.Role.BROWSER, null,
                sessions, allowedOrigins);
    }

    public static TerminalHandshakeInterceptor agent(AgentCredentialService credentials,
                                                     TerminalSessionService sessions) {
        return new TerminalHandshakeInterceptor(TerminalPeer.Role.AGENT, credentials,
                sessions, new String[0]);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String sessionId = sessionId(request.getURI());
        if (sessionId == null || request.getURI().getRawQuery() != null) {
            return false;
        }
        TerminalPeer peer = role == TerminalPeer.Role.BROWSER
                ? authenticateBrowser(request.getHeaders(), sessionId)
                : authenticateAgent(request.getHeaders(), sessionId);
        if (peer == null) {
            return false;
        }
        attributes.put(SESSION_ID_ATTRIBUTE, sessionId);
        attributes.put(PEER_ATTRIBUTE, peer);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Ticket consumption is atomic in beforeHandshake; no secret state is retained here.
    }

    private TerminalPeer authenticateBrowser(HttpHeaders headers, String sessionId) {
        String origin = exactlyOne(headers, HttpHeaders.ORIGIN);
        String protocolHeader = exactlyOne(headers, WEBSOCKET_PROTOCOL_HEADER);
        if (origin == null || !validOrigin(origin) || !browserOrigins.contains(origin)
                || protocolHeader == null) {
            return null;
        }
        String[] protocols = protocolHeader.split(",", -1);
        if (protocols.length != 2 || !SAFE_SUBPROTOCOL.equals(protocols[0].trim())) {
            return null;
        }
        String ticketProtocol = protocols[1].trim();
        if (!ticketProtocol.startsWith(TICKET_PREFIX)) {
            return null;
        }
        String ticket = ticketProtocol.substring(TICKET_PREFIX.length());
        if (!TICKET.matcher(ticket).matches()) {
            return null;
        }
        try {
            return sessions.consumeBrowserTicket(sessionId, ticket)
                    ? new TerminalPeer(TerminalPeer.Role.BROWSER, null) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private TerminalPeer authenticateAgent(HttpHeaders headers, String sessionId) {
        if (headers.containsKey(HttpHeaders.ORIGIN)) {
            return null;
        }
        String authorization = exactlyOne(headers, HttpHeaders.AUTHORIZATION);
        String ticket = exactlyOne(headers, AGENT_TICKET_HEADER);
        if (authorization == null || ticket == null || !TICKET.matcher(ticket).matches()) {
            return null;
        }
        try {
            ProcessingAgentRecord agent = credentials.authenticate(authorization);
            if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null
                    || agent.getAgentId() == null || agent.getAgentId().isEmpty()) {
                return null;
            }
            return sessions.consumeAgentTicket(agent, sessionId, ticket)
                    ? new TerminalPeer(TerminalPeer.Role.AGENT, agent.getAgentId()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String sessionId(URI uri) {
        String prefix = role == TerminalPeer.Role.BROWSER
                ? "/terminal/v1/browser/" : "/terminal/v1/agent/";
        String path = uri == null ? null : uri.getRawPath();
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }
        String value = path.substring(prefix.length());
        return SESSION.matcher(value).matches() ? value : null;
    }

    private static String exactlyOne(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        return values != null && values.size() == 1 && values.get(0) != null
                && !values.get(0).isEmpty() ? values.get(0) : null;
    }

    private static Set<String> validateOrigins(String[] origins) {
        if (origins == null || origins.length == 0) {
            return Collections.emptySet();
        }
        Set<String> values = new HashSet<>();
        for (String origin : origins) {
            String value = origin == null ? "" : origin.trim();
            if (value.isEmpty() || "*".equals(value) || !validOrigin(value)) {
                throw new IllegalArgumentException("Terminal browser origins must be explicit origins");
            }
            values.add(value);
        }
        return Collections.unmodifiableSet(values);
    }

    private static boolean validOrigin(String value) {
        try {
            URI uri = URI.create(value);
            uri = uri.parseServerAuthority();
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getPort() <= 65535
                    && uri.getRawPath() != null && uri.getRawPath().isEmpty()
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return false;
        }
    }
}
