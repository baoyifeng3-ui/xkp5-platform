package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private ProcessingAgentCommandMapper mapper;
    private AgentCommandService service;
    private ProcessingAgentRecord agent;

    @Before
    public void setUp() {
        mapper = mock(ProcessingAgentCommandMapper.class);
        service = new AgentCommandService(mapper, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        agent = new ProcessingAgentRecord();
        agent.setAgentId("11111111-1111-4111-8111-111111111111");
        agent.setEnabled(true);
    }

    @Test
    public void createsShutdownWithRequesterAndActiveDedupKey() {
        AgentCommandView view = service.requestShutdown(agent, 7, "ADMIN");

        ArgumentCaptor<ProcessingAgentCommandRecord> saved =
                ArgumentCaptor.forClass(ProcessingAgentCommandRecord.class);
        verify(mapper).insert(saved.capture());
        ProcessingAgentCommandRecord record = saved.getValue();
        assertEquals("SHUTDOWN_SERVER", record.getCommandType());
        assertEquals(Integer.valueOf(1), record.getCommandVersion());
        assertEquals("PENDING", record.getState());
        assertEquals(Integer.valueOf(7), record.getRequesterUserId());
        assertEquals("ADMIN", record.getRequesterRole());
        assertEquals(agent.getAgentId() + ":SHUTDOWN_SERVER", record.getActiveDedupKey());
        assertEquals("{}", record.getPayloadJson());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), record.getRequestedAt());
        assertNotNull(record.getCorrelationId());
        assertEquals(record.getCommandId(), view.getCommandId());
    }

    @Test
    public void secondShutdownReturnsExistingNonTerminalCommand() {
        ProcessingAgentCommandRecord existing = command("PENDING");
        when(mapper.selectActive(agent.getAgentId(), "SHUTDOWN_SERVER")).thenReturn(existing);

        AgentCommandView view = service.requestShutdown(agent, 7, "ADMIN");

        assertEquals(existing.getCommandId(), view.getCommandId());
        verify(mapper, never()).insert(any(ProcessingAgentCommandRecord.class));
    }

    @Test
    public void leasesAtMostOnePendingCommandWithFreshToken() {
        ProcessingAgentCommandRecord pending = command("PENDING");
        when(mapper.selectNextForLease(eq(agent.getAgentId()), any(LocalDateTime.class))).thenReturn(pending);
        when(mapper.markLeased(eq(pending.getCommandId()), any(String.class), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);

        Optional<AgentCommandEnvelope> leased = service.lease(agent);

        assertTrue(leased.isPresent());
        assertEquals(pending.getCommandId(), leased.get().getCommandId());
        assertNotNull(leased.get().getLeaseToken());
        assertNotEquals(pending.getCommandId(), leased.get().getLeaseToken());
        assertEquals("SHUTDOWN_SERVER", leased.get().getType());
        assertEquals(Integer.valueOf(1), leased.get().getVersion());
        assertEquals(NOW.plusSeconds(30), leased.get().getLeaseExpiresAt());
        assertTrue(leased.get().getPayload().isObject());
    }

    @Test
    public void leaseReturnsEmptyWhenConditionalClaimLosesRace() {
        ProcessingAgentCommandRecord pending = command("PENDING");
        when(mapper.selectNextForLease(eq(agent.getAgentId()), any(LocalDateTime.class))).thenReturn(pending);
        when(mapper.markLeased(eq(pending.getCommandId()), any(String.class), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(0);

        assertFalse(service.lease(agent).isPresent());
    }

    @Test
    public void eachLeaseAttemptRecoversExpiredAndExhaustedCommandsFirst() {
        when(mapper.selectNextForLease(eq(agent.getAgentId()), any(LocalDateTime.class))).thenReturn(null);

        assertFalse(service.lease(agent).isPresent());

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(mapper).failExpiredLeases(agent.getAgentId(), now, 5, now);
        verify(mapper).requeueExpiredLeases(agent.getAgentId(), now, 5, now);
    }

    private ProcessingAgentCommandRecord command(String state) {
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId("22222222-2222-4222-8222-222222222222");
        record.setAgentId(agent.getAgentId());
        record.setCommandType("SHUTDOWN_SERVER");
        record.setCommandVersion(1);
        record.setPayloadJson("{}");
        record.setState(state);
        record.setRequesterRole("ADMIN");
        record.setCorrelationId("33333333-3333-4333-8333-333333333333");
        record.setRequestedAt(LocalDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC));
        record.setAvailableAt(record.getRequestedAt());
        record.setAttemptCount(0);
        record.setUpdatedAt(record.getRequestedAt());
        return record;
    }
}
