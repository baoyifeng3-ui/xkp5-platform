package com.match.agent.service;

import com.match.agent.persistence.AgentAuditMapper;
import com.match.agent.persistence.AgentAuditRecord;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AgentAuditServiceTest {
    @Test
    public void terminalAuditParticipatesInAuthoritativeTransaction() throws Exception {
        Method method = AgentAuditService.class.getMethod("recordTerminal", String.class,
                String.class, String.class, Integer.class, String.class, String.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
    }

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

    @Test
    public void recordsCommandIdentitySeparatelyFromRegistrationToken() {
        AgentAuditMapper mapper = mock(AgentAuditMapper.class);
        AgentAuditService service = new AgentAuditService(mapper,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC));

        service.recordCommandSuccess("COMMAND_STARTED", null, "agent-id", "command-id");

        ArgumentCaptor<AgentAuditRecord> saved = ArgumentCaptor.forClass(AgentAuditRecord.class);
        verify(mapper).insert(saved.capture());
        assertEquals("command-id", saved.getValue().getCommandId());
        assertEquals(null, saved.getValue().getTokenId());
    }

    @Test
    public void terminalAuditUsesSessionAsCorrelationWithoutTokenOrSensitiveText() {
        AgentAuditMapper mapper = mock(AgentAuditMapper.class);
        AgentAuditService service = new AgentAuditService(mapper,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC));
        String sessionId = "33333333-3333-4333-8333-333333333333";

        service.recordTerminal("TERMINAL_CLOSE", "SUCCESS", "PTY_EXITED", 7,
                "11111111-1111-4111-8111-111111111111", sessionId,
                "22222222-2222-4222-8222-222222222222");

        ArgumentCaptor<AgentAuditRecord> saved = ArgumentCaptor.forClass(AgentAuditRecord.class);
        verify(mapper).insert(saved.capture());
        assertEquals(sessionId, saved.getValue().getCorrelationId());
        assertEquals(null, saved.getValue().getTokenId());
        assertFalse(saved.getValue().toString().contains("ticket"));
        assertFalse(saved.getValue().toString().contains("terminal output"));
    }
}
