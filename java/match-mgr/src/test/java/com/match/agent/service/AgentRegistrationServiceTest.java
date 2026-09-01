package com.match.agent.service;

import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.persistence.RegistrationTokenMapper;
import com.match.agent.persistence.RegistrationTokenRecord;
import com.match.agent.web.AgentProtocolException;
import com.match.licensing.crypto.Digests;
import com.match.licensing.guard.LicenseGuard;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentRegistrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private RegistrationTokenMapper tokenMapper;
    private ProcessingAgentMapper agentMapper;
    private LicenseGuard licenseGuard;
    private AgentAuditService auditService;
    private AgentRegistrationService service;

    @Before
    public void setUp() {
        tokenMapper = mock(RegistrationTokenMapper.class);
        agentMapper = mock(ProcessingAgentMapper.class);
        licenseGuard = mock(LicenseGuard.class);
        auditService = mock(AgentAuditService.class);
        when(agentMapper.selectCount(any())).thenReturn(0);
        service = new AgentRegistrationService(tokenMapper, agentMapper, licenseGuard,
                auditService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void consumesValidTokenAndReturnsCredentialStoredOnlyAsDigest() {
        RegistrationTokenRecord token = validToken();
        when(tokenMapper.selectByDigestForUpdate(Digests.sha256("one-time-token"))).thenReturn(token);

        AgentRegistrationResponse response = service.register(validRequest());

        ArgumentCaptor<ProcessingAgentRecord> saved = ArgumentCaptor.forClass(ProcessingAgentRecord.class);
        verify(agentMapper).insert(saved.capture());
        assertNotNull(response.getAgentId());
        assertEquals(43, response.getCredential().length());
        assertEquals(Digests.sha256(response.getCredential()), saved.getValue().getCredentialDigest());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), token.getConsumedAt());
        assertEquals(response.getAgentId(), token.getRegisteredAgentId());
        verify(tokenMapper).updateById(token);
        verify(licenseGuard).requireProcessingServerCapacity(1);
        verify(auditService).recordSuccess("AGENT_REGISTERED", null, response.getAgentId(), "token-id");
    }

    @Test
    public void rejectsExpiredTokenWithoutCreatingAgent() {
        RegistrationTokenRecord token = validToken();
        token.setExpiresAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(tokenMapper.selectByDigestForUpdate(Digests.sha256("one-time-token"))).thenReturn(token);

        assertProtocolCode("TOKEN_EXPIRED", () -> service.register(validRequest()));
    }

    @Test
    public void refreshesCredentialForAlreadyRegisteredMachine() {
        RegistrationTokenRecord token = validToken();
        when(tokenMapper.selectByDigestForUpdate(Digests.sha256("one-time-token"))).thenReturn(token);
        ProcessingAgentRecord existing = new ProcessingAgentRecord();
        existing.setAgentId("existing-agent");
        when(agentMapper.selectByMachineDigest(validRequest().getMachineDigest())).thenReturn(existing);
        when(agentMapper.refreshRegistration(org.mockito.ArgumentMatchers.eq("existing-agent"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(1);

        AgentRegistrationResponse response = service.register(validRequest());
        assertEquals("existing-agent", response.getAgentId());
        assertEquals(43, response.getCredential().length());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), token.getConsumedAt());
        verify(tokenMapper).updateById(token);
    }

    private RegistrationTokenRecord validToken() {
        RegistrationTokenRecord token = new RegistrationTokenRecord();
        token.setTokenId("token-id");
        token.setTokenDigest(Digests.sha256("one-time-token"));
        token.setExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        return token;
    }

    private AgentRegistrationRequest validRequest() {
        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setToken("one-time-token");
        request.setDisplayName("处理服务器 1");
        request.setMachineDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setHostname("worker-01");
        request.setPrimaryIp("192.168.1.21");
        request.setMacAddress("00:11:22:33:44:55");
        request.setAgentVersion("0.1.0");
        return request;
    }

    private void assertProtocolCode(String code, Runnable action) {
        try {
            action.run();
        } catch (AgentProtocolException exception) {
            assertEquals(code, exception.getCode());
            return;
        }
        throw new AssertionError("expected AgentProtocolException");
    }
}
