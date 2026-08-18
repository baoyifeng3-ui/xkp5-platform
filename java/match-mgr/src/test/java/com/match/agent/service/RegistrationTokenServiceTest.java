package com.match.agent.service;

import com.match.agent.model.RegistrationTokenView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.RegistrationTokenMapper;
import com.match.agent.persistence.RegistrationTokenRecord;
import com.match.licensing.crypto.Digests;
import com.match.licensing.guard.LicenseGuard;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegistrationTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private RegistrationTokenMapper tokenMapper;
    private ProcessingAgentMapper agentMapper;
    private LicenseGuard licenseGuard;
    private AgentAuditService auditService;
    private RegistrationTokenService service;

    @Before
    public void setUp() {
        tokenMapper = mock(RegistrationTokenMapper.class);
        agentMapper = mock(ProcessingAgentMapper.class);
        licenseGuard = mock(LicenseGuard.class);
        auditService = mock(AgentAuditService.class);
        when(agentMapper.selectCount(any())).thenReturn(2);
        service = new RegistrationTokenService(tokenMapper, agentMapper, licenseGuard,
                auditService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void createsTenMinuteSingleDisplayTokenAndStoresOnlyDigest() {
        RegistrationTokenView issued = service.create(7, "机房 A");

        ArgumentCaptor<RegistrationTokenRecord> saved = ArgumentCaptor.forClass(RegistrationTokenRecord.class);
        verify(tokenMapper).insert(saved.capture());
        verify(licenseGuard).requireProcessingServerCapacity(3);
        assertNotNull(issued.getToken());
        assertEquals(43, issued.getToken().length());
        assertEquals(LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC), issued.getExpiresAt());
        assertEquals(Digests.sha256(issued.getToken()), saved.getValue().getTokenDigest());
        assertFalse(saved.getValue().getTokenDigest().contains(issued.getToken()));
        verify(auditService).recordSuccess("AGENT_TOKEN_CREATED", 7, null, saved.getValue().getTokenId());
    }

    @Test
    public void tokenListNeverReturnsPlaintextOrDigest() {
        RegistrationTokenRecord record = new RegistrationTokenRecord();
        record.setTokenId("token-id");
        record.setTokenDigest("secret-digest");
        record.setLabel("机房 A");
        record.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        record.setExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
        when(tokenMapper.selectList(any())).thenReturn(Collections.singletonList(record));

        RegistrationTokenView listed = service.list().get(0);

        assertEquals("token-id", listed.getTokenId());
        assertNull(listed.getToken());
        assertFalse(listed.toString().contains("secret-digest"));
    }
}
