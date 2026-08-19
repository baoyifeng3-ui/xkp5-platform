package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private ProcessingAgentCommandMapper mapper;
    private AgentCommandService service;
    private AgentAuditService audit;
    private ProcessingAgentRecord agent;

    @Before
    public void setUp() {
        mapper = mock(ProcessingAgentCommandMapper.class);
        audit = mock(AgentAuditService.class);
        service = new AgentCommandService(mapper, new ObjectMapper().findAndRegisterModules(),
                audit, Clock.fixed(NOW, ZoneOffset.UTC));
        agent = new ProcessingAgentRecord();
        agent.setAgentId("11111111-1111-4111-8111-111111111111");
        agent.setEnabled(true);
        when(mapper.selectEnabledAgentForUpdate(agent.getAgentId())).thenReturn(agent.getAgentId());
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
    public void concurrentDuplicateEnvironmentCommandReturnsTheExistingCommand() {
        String dedupKey = "environment-1:CREATE";
        ProcessingAgentCommandRecord existing = command("PENDING");
        existing.setCommandType("CREATE_TRAINING_ENVIRONMENT");
        existing.setActiveDedupKey(dedupKey);
        when(mapper.selectActiveByDedup(agent.getAgentId(), existing.getCommandType(), dedupKey))
                .thenReturn(null, existing);
        doThrow(new DuplicateKeyException("duplicate active command"))
                .when(mapper).insert(any(ProcessingAgentCommandRecord.class));

        AgentCommandView view = service.requestEnvironmentCommand(agent, existing.getCommandType(),
                "{}", 7, "SUPER_ADMIN", dedupKey);

        assertEquals(existing.getCommandId(), view.getCommandId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void environmentCommandPayloadCannotExceedAgentEnvelopeBudget() {
        service.requestEnvironmentCommand(agent, "CREATE_TRAINING_ENVIRONMENT",
                repeat('x', 3073), 7, "SUPER_ADMIN", "environment-1:CREATE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shutdownRequestRechecksEnabledAgentInsideTransaction() {
        when(mapper.selectEnabledAgentForUpdate(agent.getAgentId())).thenReturn(null);

        service.requestShutdown(agent, 7, "ADMIN");
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
        verify(audit).recordCommandSuccess("COMMAND_LEASED", null, agent.getAgentId(), pending.getCommandId());
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
    public void leaseDoesNotRequireTheAgentToRecoverExpiredCommands() {
        when(mapper.selectNextForLease(eq(agent.getAgentId()), any(LocalDateTime.class))).thenReturn(null);

        assertFalse(service.lease(agent).isPresent());

        verify(mapper, never()).failExpiredLeases(eq(agent.getAgentId()), any(LocalDateTime.class),
                eq(5), any(LocalDateTime.class));
        verify(mapper, never()).requeueExpiredLeases(eq(agent.getAgentId()), any(LocalDateTime.class),
                eq(5), any(LocalDateTime.class));
    }

    @Test
    public void matchingLeaseStartsCommand() {
        ProcessingAgentCommandRecord leased = leasedCommand("LEASED");
        when(mapper.markRunning(leased.getCommandId(), agent.getAgentId(), leased.getLeaseToken(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(1);

        AgentCommandView view = service.start(agent, leased.getCommandId(),
                new AgentCommandStartRequest(leased.getLeaseToken()));

        assertEquals("RUNNING", view.getState());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), view.getStartedAt());
        verify(audit).recordCommandSuccess("COMMAND_STARTED", null, agent.getAgentId(), leased.getCommandId());
    }

    @Test
    public void staleLeaseCannotStartCommand() {
        ProcessingAgentCommandRecord leased = leasedCommand("LEASED");
        when(mapper.selectById(leased.getCommandId())).thenReturn(leased);

        expectConflict(() -> service.start(agent, leased.getCommandId(),
                new AgentCommandStartRequest("44444444-4444-4444-8444-444444444444")));
    }

    @Test
    public void foreignAgentCannotStartCommand() {
        ProcessingAgentCommandRecord leased = leasedCommand("LEASED");
        leased.setAgentId("55555555-5555-4555-8555-555555555555");
        when(mapper.selectById(leased.getCommandId())).thenReturn(leased);

        expectConflict(() -> service.start(agent, leased.getCommandId(),
                new AgentCommandStartRequest(leased.getLeaseToken())));
    }

    @Test
    public void runningCommandAcceptsSuccessfulResult() {
        ProcessingAgentCommandRecord running = leasedCommand("RUNNING");
        AgentCommandResultRequest result = result(true, "SHUTDOWN_ACCEPTED", "systemd accepted poweroff");
        when(mapper.recordShutdownAccepted(eq(running.getCommandId()), eq(agent.getAgentId()),
                eq(running.getLeaseToken()), any(LocalDateTime.class),
                eq("SHUTDOWN_ACCEPTED"), eq("systemd accepted poweroff"), eq(null))).thenReturn(1);

        AgentCommandView view = service.finish(agent, running.getCommandId(), result);

        assertEquals("RUNNING", view.getState());
        assertEquals("SHUTDOWN_ACCEPTED", view.getResultCode());
        assertEquals(null, view.getCompletedAt());
        verify(mapper, never()).markTerminal(eq(running.getCommandId()), eq(agent.getAgentId()),
                eq(running.getLeaseToken()), eq("SUCCEEDED"), any(LocalDateTime.class),
                any(String.class), any(String.class), any(String.class));
        verify(audit).recordCommandSuccess("SHUTDOWN_ACCEPTED", null, agent.getAgentId(), running.getCommandId());
    }

    @Test
    public void environmentCommandAcceptsSuccessfulResultAsTerminal() {
        ProcessingAgentCommandRecord running = leasedCommand("RUNNING");
        running.setCommandType("CREATE_TRAINING_ENVIRONMENT");
        when(mapper.selectById(running.getCommandId())).thenReturn(running);
        when(mapper.markTerminal(eq(running.getCommandId()), eq(agent.getAgentId()),
                eq(running.getLeaseToken()), eq("SUCCEEDED"), any(LocalDateTime.class),
                eq("ENVIRONMENT_CREATED"), eq("container pair created"), eq(null))).thenReturn(1);

        AgentCommandView view = service.finish(agent, running.getCommandId(),
                result(true, "ENVIRONMENT_CREATED", "container pair created"));

        assertEquals("SUCCEEDED", view.getState());
        assertNotNull(view.getCompletedAt());
        assertEquals("ENVIRONMENT_CREATED", view.getResultCode());
        verify(audit).recordCommandSuccess("COMMAND_RESULT", null, agent.getAgentId(), running.getCommandId());
        verify(audit, never()).recordCommandFailure(eq("COMMAND_RESULT"), any(String.class),
                eq(null), eq(agent.getAgentId()), eq(running.getCommandId()));
    }

    @Test
    public void duplicateIdenticalTerminalResultIsIdempotent() {
        ProcessingAgentCommandRecord completed = leasedCommand("RUNNING");
        completed.setResultCode("SHUTDOWN_ACCEPTED");
        completed.setResultMessage("systemd accepted poweroff");
        completed.setCompletedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(mapper.selectById(completed.getCommandId())).thenReturn(completed);

        AgentCommandView view = service.finish(agent, completed.getCommandId(),
                result(true, "SHUTDOWN_ACCEPTED", "systemd accepted poweroff"));

        assertEquals("RUNNING", view.getState());
    }

    @Test
    public void recoveredAgentCanAcknowledgeAlreadyOfflineConfirmedShutdown() {
        ProcessingAgentCommandRecord completed = leasedCommand("SUCCEEDED");
        completed.setResultCode("OFFLINE_CONFIRMED");
        completed.setResultMessage("Agent heartbeat stopped after shutdown started");
        completed.setCompletedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(mapper.selectById(completed.getCommandId())).thenReturn(completed);

        AgentCommandView view = service.finish(agent, completed.getCommandId(),
                result(false, "EXECUTION_OUTCOME_UNKNOWN",
                        "Agent restarted before command outcome was recorded"));

        assertEquals("SUCCEEDED", view.getState());
        assertEquals("OFFLINE_CONFIRMED", view.getResultCode());
    }

    @Test
    public void conflictingTerminalResultIsRejected() {
        ProcessingAgentCommandRecord completed = leasedCommand("FAILED");
        completed.setResultCode("SHUTDOWN_FAILED");
        completed.setResultMessage("permission denied");
        when(mapper.selectById(completed.getCommandId())).thenReturn(completed);

        expectConflict(() -> service.finish(agent, completed.getCommandId(),
                result(true, "SHUTDOWN_ACCEPTED", "systemd accepted poweroff")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void resultMessageCannotExceedBound() {
        service.finish(agent, "22222222-2222-4222-8222-222222222222",
                result(false, "SHUTDOWN_FAILED", repeat('x', 513)));
    }

    @Test
    public void recentHistoryReturnsPublicViewsOnly() {
        ProcessingAgentCommandRecord completed = command("SUCCEEDED");
        completed.setLeaseToken("secret-lease-token");
        completed.setResultCode("OFFLINE_CONFIRMED");
        when(mapper.selectRecent(agent.getAgentId(), 50)).thenReturn(Collections.singletonList(completed));

        List<AgentCommandView> history = service.recent(agent.getAgentId());

        assertEquals(1, history.size());
        assertEquals(completed.getCommandId(), history.get(0).getCommandId());
        assertEquals("OFFLINE_CONFIRMED", history.get(0).getResultCode());
        assertFalse(history.get(0).toString().contains("secret-lease-token"));
        verify(mapper).selectRecent(agent.getAgentId(), 50);
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

    private ProcessingAgentCommandRecord leasedCommand(String state) {
        ProcessingAgentCommandRecord record = command(state);
        record.setLeaseToken("66666666-6666-4666-8666-666666666666");
        record.setLeaseExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC));
        record.setDeliveredAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        record.setAttemptCount(1);
        return record;
    }

    private AgentCommandResultRequest result(boolean success, String code, String message) {
        AgentCommandResultRequest request = new AgentCommandResultRequest();
        request.setLeaseToken("66666666-6666-4666-8666-666666666666");
        request.setSuccess(success);
        request.setCode(code);
        request.setMessage(message);
        return request;
    }

    private void expectConflict(Runnable action) {
        try {
            action.run();
        } catch (AgentProtocolException exception) {
            assertEquals("COMMAND_LEASE_CONFLICT", exception.getCode());
            return;
        }
        throw new AssertionError("expected command lease conflict");
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
