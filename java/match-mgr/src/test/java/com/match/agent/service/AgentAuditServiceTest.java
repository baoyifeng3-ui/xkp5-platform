package com.match.agent.service;

import com.match.agent.persistence.AgentAuditMapper;
import com.match.agent.persistence.AgentAuditRecord;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AgentAuditServiceTest {
    @Test
    public void recordsSafeMetadataWithoutCredentials() {
        AgentAuditMapper mapper = mock(AgentAuditMapper.class);
        AgentAuditService service = new AgentAuditService(mapper,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC));

        service.recordSuccess("AGENT_TOKEN_CREATED", 7, null, "token-id");

        ArgumentCaptor<AgentAuditRecord> saved = ArgumentCaptor.forClass(AgentAuditRecord.class);
        verify(mapper).insert(saved.capture());
        assertEquals("AGENT_TOKEN_CREATED", saved.getValue().getAction());
        assertEquals("SUCCESS", saved.getValue().getResult());
        assertEquals("token-id", saved.getValue().getTokenId());
        assertFalse(saved.getValue().toString().contains("credential"));
    }
}
