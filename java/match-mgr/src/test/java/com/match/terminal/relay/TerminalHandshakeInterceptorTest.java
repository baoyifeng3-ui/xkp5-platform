package com.match.terminal.relay;

import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCredentialService;
import com.match.terminal.service.TerminalSessionService;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalHandshakeInterceptorTest {
    private static final String SESSION_ID = "33333333-3333-4333-8333-333333333333";
    private static final String TICKET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO12";

    @Test
    public void browserRequiresExactOriginAndClosedSubprotocolTicket() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        when(sessions.consumeBrowserTicket(SESSION_ID, TICKET)).thenReturn(true, false);
        TerminalHandshakeInterceptor interceptor = TerminalHandshakeInterceptor.browser(
                sessions, new String[]{"https://management.example"});
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request(
                "/terminal/v1/browser/" + SESSION_ID,
                header(HttpHeaders.ORIGIN, "https://management.example",
                        TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                        "xkp-terminal-v1, xkp-terminal-ticket." + TICKET)),
                mock(ServerHttpResponse.class), null, attributes));
        assertEquals(SESSION_ID, attributes.get(TerminalHandshakeInterceptor.SESSION_ID_ATTRIBUTE));
        TerminalPeer peer = (TerminalPeer) attributes.get(TerminalHandshakeInterceptor.PEER_ATTRIBUTE);
        assertEquals(TerminalPeer.Role.BROWSER, peer.role());
        assertNull(peer.agentId());
        assertFalse(attributes.toString().contains(TICKET));

        assertFalse(interceptor.beforeHandshake(request(
                "/terminal/v1/browser/" + SESSION_ID,
                header(HttpHeaders.ORIGIN, "https://management.example",
                        TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                        "xkp-terminal-v1, xkp-terminal-ticket." + TICKET)),
                mock(ServerHttpResponse.class), null, new HashMap<>()));
    }

    @Test
    public void browserRejectsAbsentMultipleMalformedOrUnlistedOriginsAndProtocolForms() {
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        TerminalHandshakeInterceptor interceptor = TerminalHandshakeInterceptor.browser(
                sessions, new String[]{"https://management.example"});

        assertRejected(interceptor, "/terminal/v1/browser/" + SESSION_ID,
                header(TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                        "xkp-terminal-v1, xkp-terminal-ticket." + TICKET));
        HttpHeaders multipleOrigins = validBrowserHeaders();
        multipleOrigins.add(HttpHeaders.ORIGIN, "https://other.example");
        assertRejected(interceptor, "/terminal/v1/browser/" + SESSION_ID, multipleOrigins);
        assertRejected(interceptor, "/terminal/v1/browser/" + SESSION_ID,
                header(HttpHeaders.ORIGIN, "https://evil.example",
                        TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                        "xkp-terminal-v1, xkp-terminal-ticket." + TICKET));
        assertRejected(interceptor, "/terminal/v1/browser/" + SESSION_ID,
                header(HttpHeaders.ORIGIN, "https://management.example",
                        TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                        "xkp-terminal-ticket." + TICKET));
        assertRejected(interceptor, "/terminal/v1/browser/not-a-session", validBrowserHeaders());
        verify(sessions, never()).consumeBrowserTicket(anyString(), anyString());
    }

    @Test
    public void agentAuthenticatesSingleBearerAndSubprotocolTicketWithoutOriginAndBindsIdentity() {
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-1");
        agent.setEnabled(true);
        when(credentials.authenticate("Bearer credential")).thenReturn(agent);
        when(sessions.consumeAgentTicket(agent, SESSION_ID, TICKET)).thenReturn(true);
        TerminalHandshakeInterceptor interceptor = TerminalHandshakeInterceptor.agent(credentials, sessions);
        Map<String, Object> attributes = new HashMap<>();

        HttpHeaders headers = header(HttpHeaders.AUTHORIZATION, "Bearer credential",
                TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                "xkp-terminal-v1, xkp-terminal-ticket." + TICKET);
        assertTrue(interceptor.beforeHandshake(request("/terminal/v1/agent/" + SESSION_ID, headers),
                mock(ServerHttpResponse.class), null, attributes));

        TerminalPeer peer = (TerminalPeer) attributes.get(TerminalHandshakeInterceptor.PEER_ATTRIBUTE);
        assertEquals(TerminalPeer.Role.AGENT, peer.role());
        assertEquals("agent-1", peer.agentId());
        assertFalse(attributes.toString().contains("credential"));
        assertFalse(attributes.toString().contains(TICKET));
    }

    @Test
    public void agentRejectsOriginMultipleHeadersRemovedAgentAndTicketReplay() {
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-1");
        agent.setEnabled(true);
        when(credentials.authenticate("Bearer credential")).thenReturn(agent);
        when(sessions.consumeAgentTicket(agent, SESSION_ID, TICKET)).thenReturn(false);
        TerminalHandshakeInterceptor interceptor = TerminalHandshakeInterceptor.agent(credentials, sessions);

        HttpHeaders origin = validAgentHeaders();
        origin.add(HttpHeaders.ORIGIN, "https://management.example");
        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID, origin);
        HttpHeaders multiple = validAgentHeaders();
        multiple.add(TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                "xkp-terminal-v1, xkp-terminal-ticket." + TICKET);
        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID, multiple);
        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID, validAgentHeaders());

        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID,
                header(HttpHeaders.AUTHORIZATION, "Bearer credential",
                        TerminalHandshakeInterceptor.AGENT_TICKET_HEADER, TICKET));

        agent.setRemovedAt(java.time.LocalDateTime.now());
        when(sessions.consumeAgentTicket(agent, SESSION_ID, TICKET)).thenReturn(true);
        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID, validAgentHeaders());
    }

    @Test
    public void agentHandshakeRejectsOfflineIdentityWhenAtomicConsumeRefusesIt() {
        AgentCredentialService credentials = mock(AgentCredentialService.class);
        TerminalSessionService sessions = mock(TerminalSessionService.class);
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId("agent-1");
        agent.setEnabled(true);
        agent.setLastSeenAt(LocalDateTime.of(2026, 8, 19, 11, 59, 44));
        when(credentials.authenticate("Bearer credential")).thenReturn(agent);
        when(sessions.consumeAgentTicket(agent, SESSION_ID, TICKET)).thenReturn(false);
        TerminalHandshakeInterceptor interceptor = TerminalHandshakeInterceptor.agent(credentials, sessions);

        assertRejected(interceptor, "/terminal/v1/agent/" + SESSION_ID, validAgentHeaders());

        verify(sessions).consumeAgentTicket(agent, SESSION_ID, TICKET);
    }

    private void assertRejected(TerminalHandshakeInterceptor interceptor, String path,
                                HttpHeaders headers) {
        assertFalse(interceptor.beforeHandshake(request(path, headers),
                mock(ServerHttpResponse.class), null, new HashMap<>()));
    }

    private HttpHeaders validBrowserHeaders() {
        return header(HttpHeaders.ORIGIN, "https://management.example",
                TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                "xkp-terminal-v1, xkp-terminal-ticket." + TICKET);
    }

    private HttpHeaders validAgentHeaders() {
        return header(HttpHeaders.AUTHORIZATION, "Bearer credential",
                TerminalHandshakeInterceptor.WEBSOCKET_PROTOCOL_HEADER,
                "xkp-terminal-v1, xkp-terminal-ticket." + TICKET);
    }

    private ServerHttpRequest request(String path, HttpHeaders headers) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("https://relay.example" + path));
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private HttpHeaders header(String firstName, String firstValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(firstName, firstValue);
        return headers;
    }

    private HttpHeaders header(String firstName, String firstValue,
                               String secondName, String secondValue) {
        HttpHeaders headers = header(firstName, firstValue);
        headers.add(secondName, secondValue);
        return headers;
    }
}
