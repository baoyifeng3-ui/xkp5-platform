package com.match.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.lang.reflect.Method;

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
    public void validatesRunningTerminalLeaseThroughExactMapperContract() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        when(mapper.selectRunningTerminalLeaseForUpdate(record.getCommandId(),
                agent.getAgentId(), "lease", "session")).thenReturn(record);

        assertTrue(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), null, "session"));
        verify(mapper).selectRunningTerminalLeaseForUpdate(record.getCommandId(),
                agent.getAgentId(), "lease", "session");
    }

    @Test
    public void pendingStateCannotAuthorizeTerminalLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        record.setState("PENDING");
        stubRunningTerminalLease(record, "session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
    }

    @Test
    public void lowercaseRunningStateCannotAuthorizeTerminalLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        record.setState("running");
        stubRunningTerminalLease(record, "session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
    }

    @Test
    public void differentCommandTypeCannotAuthorizeTerminalLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        record.setCommandType("SHUTDOWN_SERVER");
        stubRunningTerminalLease(record, "session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
    }

    @Test
    public void lowercaseTerminalTypeCannotAuthorizeTerminalLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        record.setCommandType("open_root_terminal");
        stubRunningTerminalLease(record, "session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
    }

    @Test
    public void mixedCaseTerminalTypeCannotAuthorizeTerminalLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("session");
        record.setCommandType("Open_Root_Terminal");
        stubRunningTerminalLease(record, "session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "session"));
    }

    @Test
    public void terminalPayloadBoundToForeignSessionCannotAuthorizeLease() {
        ProcessingAgentCommandRecord record = runningTerminalLease("foreign-session");
        stubRunningTerminalLease(record, "requested-session");

        assertFalse(service.hasRunningTerminalLease(record.getCommandId(),
                agent.getAgentId(), "lease", "requested-session"));
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

    @Test
    public void createsTicketFreeRootTerminalCommandWithClosedPayload() throws Exception {
        String sessionId = "44444444-4444-4444-8444-444444444444";
        Instant connectionDeadline = NOW.plusSeconds(90);
        Instant absoluteExpiry = NOW.plusSeconds(7200);

        AgentCommandView view = service.requestTerminalCommand(agent, sessionId,
                connectionDeadline, absoluteExpiry, 7, "SUPER_ADMIN");

        ArgumentCaptor<ProcessingAgentCommandRecord> saved =
                ArgumentCaptor.forClass(ProcessingAgentCommandRecord.class);
        verify(mapper).insert(saved.capture());
        ProcessingAgentCommandRecord record = saved.getValue();
        assertEquals("OPEN_ROOT_TERMINAL", record.getCommandType());
        assertEquals(agent.getAgentId() + ":OPEN_ROOT_TERMINAL", record.getActiveDedupKey());
        assertEquals("SUPER_ADMIN", record.getRequesterRole());
        JsonNode payload = new ObjectMapper().readTree(record.getPayloadJson());
        Set<String> keys = new HashSet<>();
        Iterator<String> names = payload.fieldNames();
        while (names.hasNext()) keys.add(names.next());
        assertEquals(new HashSet<>(java.util.Arrays.asList("sessionId", "relayUrl",
                "agentConnectionDeadline", "idleTimeoutSeconds", "absoluteExpiresAt")), keys);
        assertEquals(sessionId, payload.get("sessionId").asText());
        assertEquals("wss://127.0.0.1:19147/terminal/v1/agent/" + sessionId,
                payload.get("relayUrl").asText());
        assertEquals(connectionDeadline.toString(), payload.get("agentConnectionDeadline").asText());
        assertEquals(600, payload.get("idleTimeoutSeconds").asInt());
        assertEquals(absoluteExpiry.toString(), payload.get("absoluteExpiresAt").asText());
        assertFalse(record.getPayloadJson().toLowerCase().contains("ticket"));
        assertEquals(record.getCommandId(), view.getCommandId());
    }

    @Test
    public void concurrentDuplicateTerminalCommandReturnsExistingCommand() {
        String sessionId = "44444444-4444-4444-8444-444444444444";
        String dedupKey = agent.getAgentId() + ":OPEN_ROOT_TERMINAL";
        ProcessingAgentCommandRecord existing = command("PENDING");
        existing.setCommandType("OPEN_ROOT_TERMINAL");
        existing.setActiveDedupKey(dedupKey);
        existing.setPayloadJson(terminalPayload(sessionId));
        when(mapper.selectActiveByDedup(agent.getAgentId(), "OPEN_ROOT_TERMINAL", dedupKey))
                .thenReturn(null, existing);
        doThrow(new DuplicateKeyException("duplicate active command"))
                .when(mapper).insert(any(ProcessingAgentCommandRecord.class));

        AgentCommandView view = service.requestTerminalCommand(agent, sessionId,
                NOW.plusSeconds(90), NOW.plusSeconds(7200), 7, "SUPER_ADMIN");

        assertEquals(existing.getCommandId(), view.getCommandId());
    }

    @Test
    public void sameSessionTerminalCommandIsReusedOnlyWithExactApprovedPayload() {
        String sessionId = "44444444-4444-4444-8444-444444444444";
        ProcessingAgentCommandRecord existing = activeTerminalCommand(sessionId,
                terminalPayload(sessionId));
        when(mapper.selectActiveByDedup(agent.getAgentId(), "OPEN_ROOT_TERMINAL",
                agent.getAgentId() + ":OPEN_ROOT_TERMINAL")).thenReturn(existing);

        AgentCommandView view = requestTerminal(sessionId);

        assertEquals(existing.getCommandId(), view.getCommandId());
        verify(mapper, never()).insert(any(ProcessingAgentCommandRecord.class));
    }

    @Test
    public void activeTerminalCommandForDifferentSessionIsRejected() {
        String requestedSession = "44444444-4444-4444-8444-444444444444";
        String foreignSession = "55555555-5555-4555-8555-555555555555";
        stubActiveTerminal(activeTerminalCommand(foreignSession, terminalPayload(foreignSession)));

        expectTerminalSessionConflict(() -> requestTerminal(requestedSession));

        verify(mapper, never()).insert(any(ProcessingAgentCommandRecord.class));
    }

    @Test
    public void malformedOrNonSchemaTerminalPayloadIsRejected() {
        String sessionId = "44444444-4444-4444-8444-444444444444";
        String[] invalidPayloads = new String[]{
                "{}",
                "{not-json",
                terminalPayload(sessionId).replace("}", ",\"unknownField\":true}")
        };
        for (String payload : invalidPayloads) {
            org.mockito.Mockito.reset(mapper);
            when(mapper.selectEnabledAgentForUpdate(agent.getAgentId())).thenReturn(agent.getAgentId());
            stubActiveTerminal(activeTerminalCommand(sessionId, payload));
            expectTerminalSessionConflict(() -> requestTerminal(sessionId));
        }
    }

    @Test
    public void terminalCommandRejectsWrongVersionAndNonExactIdleTimeoutInteger() {
        String sessionId = "44444444-4444-4444-8444-444444444444";
        ProcessingAgentCommandRecord wrongVersion = activeTerminalCommand(sessionId,
                terminalPayload(sessionId));
        wrongVersion.setCommandVersion(2);
        stubActiveTerminal(wrongVersion);
        expectTerminalSessionConflict(() -> requestTerminal(sessionId));

        org.mockito.Mockito.reset(mapper);
        when(mapper.selectEnabledAgentForUpdate(agent.getAgentId())).thenReturn(agent.getAgentId());
        ProcessingAgentCommandRecord hugeInteger = activeTerminalCommand(sessionId,
                terminalPayload(sessionId).replace("\"idleTimeoutSeconds\":600",
                        "\"idleTimeoutSeconds\":4294967896"));
        stubActiveTerminal(hugeInteger);
        expectTerminalSessionConflict(() -> requestTerminal(sessionId));
    }

    @Test
    public void duplicateTerminalCollisionWithForeignSessionIsRejected() {
        String requestedSession = "44444444-4444-4444-8444-444444444444";
        String foreignSession = "55555555-5555-4555-8555-555555555555";
        ProcessingAgentCommandRecord foreign = activeTerminalCommand(foreignSession,
                terminalPayload(foreignSession));
        String dedupKey = agent.getAgentId() + ":OPEN_ROOT_TERMINAL";
        when(mapper.selectActiveByDedup(agent.getAgentId(), "OPEN_ROOT_TERMINAL", dedupKey))
                .thenReturn(null, foreign);
        doThrow(new DuplicateKeyException("duplicate active command"))
                .when(mapper).insert(any(ProcessingAgentCommandRecord.class));

        expectTerminalSessionConflict(() -> requestTerminal(requestedSession));
    }

    @Test
    public void terminalCommandDoesNotReuseACommandWithAnotherDedupKey() {
        ProcessingAgentCommandRecord unrelated = command("PENDING");
        unrelated.setCommandType("OPEN_ROOT_TERMINAL");
        unrelated.setActiveDedupKey("unrelated-key");
        when(mapper.selectActive(agent.getAgentId(), "OPEN_ROOT_TERMINAL")).thenReturn(unrelated);

        AgentCommandView created = service.requestTerminalCommand(agent,
                "44444444-4444-4444-8444-444444444444", NOW.plusSeconds(90),
                NOW.plusSeconds(7200), 7, "SUPER_ADMIN");

        assertNotEquals(unrelated.getCommandId(), created.getCommandId());
        verify(mapper).insert(any(ProcessingAgentCommandRecord.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void terminalCommandRequiresSuperAdmin() {
        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                NOW.plusSeconds(90), NOW.plusSeconds(7200), 7, "ADMIN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void terminalCommandRejectsAbsoluteExpiryBeyondTwoHours() {
        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                NOW.plusSeconds(90), NOW.plusSeconds(7201), 7, "SUPER_ADMIN");
    }

    @Test
    public void terminalCommandAcceptsNinetySecondConnectionDeadline() {
        AgentCommandView created = service.requestTerminalCommand(agent,
                "44444444-4444-4444-8444-444444444444", NOW.plusSeconds(90),
                NOW.plusSeconds(7200), 7, "SUPER_ADMIN");

        assertNotNull(created.getCommandId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void terminalCommandRejectsNinetyOneSecondConnectionDeadline() {
        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                NOW.plusSeconds(91), NOW.plusSeconds(7200), 7, "SUPER_ADMIN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void terminalCommandRejectsExpiredConnectionDeadline() {
        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                NOW, NOW.plusSeconds(7200), 7, "SUPER_ADMIN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void terminalCommandRejectsSubsecondDeadlines() {
        service.requestTerminalCommand(agent, "44444444-4444-4444-8444-444444444444",
                NOW.plusSeconds(90).plusNanos(1), NOW.plusSeconds(7200), 7, "SUPER_ADMIN");
    }

    @Test
    public void terminalCommandIsTransactionalAndRejectsUnsafeRelayConfiguration() throws Exception {
        Method method = AgentCommandService.class.getMethod("requestTerminalCommand",
                ProcessingAgentRecord.class, String.class, Instant.class, Instant.class,
                Integer.class, String.class);
        assertNotNull(method.getAnnotation(Transactional.class));

        expectIllegalArgument(() -> terminalService("ws://relay.example/terminal/v1/agent", false));
        expectIllegalArgument(() -> terminalService(
                "wss://user:secret@relay.example/terminal/v1/agent", false));
        expectIllegalArgument(() -> terminalService(
                "wss://relay.example/terminal/v1/agent?credential=secret", false));
        expectIllegalArgument(() -> terminalService(
                "wss://127.0.0.1:19147/terminal/v1/agent", false));
        expectIllegalArgument(() -> terminalService(
                "ws://relay.example/terminal/v1/agent", true));
        expectIllegalArgument(() -> terminalService(
                "ws://127.0.0.2:19147/terminal/v1/agent", true));
        expectIllegalArgument(() -> terminalService(
                "ws://0.0.0.0:19147/terminal/v1/agent", true));
        expectIllegalArgument(() -> terminalService(
                "ws://127.0.0.1:19147/terminal/v1/browser", true));
        assertNotNull(terminalService("ws://127.0.0.1:19147/terminal/v1/agent", true));
        assertNotNull(terminalService("ws://localhost/terminal/v1/agent", true));
        assertNotNull(terminalService("ws://[::1]:19147/terminal/v1/agent", true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void environmentCommandPayloadCannotExceedAgentEnvelopeBudget() {
        service.requestEnvironmentCommand(agent, "CREATE_TRAINING_ENVIRONMENT",
                repeat('x', 3073), 7, "SUPER_ADMIN", "environment-1:CREATE");
    }

    @Test
    public void normalUserCanRequestOnlyAnEnvironmentCommand() {
        service.requestEnvironmentCommand(agent, "START_TRAINING_ENVIRONMENT", "{}",
                21, "USER", "environment-1:START");

        ArgumentCaptor<ProcessingAgentCommandRecord> saved =
                ArgumentCaptor.forClass(ProcessingAgentCommandRecord.class);
        verify(mapper).insert(saved.capture());
        assertEquals("USER", saved.getValue().getRequesterRole());
        assertEquals("START_TRAINING_ENVIRONMENT", saved.getValue().getCommandType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalUserCannotRequestServerShutdown() {
        service.requestShutdown(agent, 21, "USER");
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
    public void terminalResultPublishesFinishedEventOnlyAfterFirstSuccessfulUpdate() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        service = new AgentCommandService(mapper, new ObjectMapper().findAndRegisterModules(),
                audit, Clock.fixed(NOW, ZoneOffset.UTC), null,
                "wss://127.0.0.1:19147/terminal/v1/agent", true, publisher);
        ProcessingAgentCommandRecord running = leasedCommand("RUNNING");
        running.setCommandType("OPEN_ROOT_TERMINAL");
        ProcessingAgentCommandRecord completed = leasedCommand("FAILED");
        completed.setCommandType("OPEN_ROOT_TERMINAL");
        completed.setResultCode("PTY_START_FAILED");
        completed.setResultMessage("secret output");
        when(mapper.selectById(running.getCommandId())).thenReturn(running, completed);
        when(mapper.markTerminal(eq(running.getCommandId()), eq(agent.getAgentId()),
                eq(running.getLeaseToken()), eq("FAILED"), any(LocalDateTime.class),
                eq("PTY_START_FAILED"), eq("secret output"), eq(null))).thenReturn(1, 0);

        service.finish(agent, running.getCommandId(),
                result(false, "PTY_START_FAILED", "secret output"));
        service.finish(agent, running.getCommandId(),
                result(false, "PTY_START_FAILED", "secret output"));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        AgentCommandFinishedEvent finished = (AgentCommandFinishedEvent) event.getValue();
        assertEquals("OPEN_ROOT_TERMINAL", finished.getCommandType());
        assertEquals("PTY_START_FAILED", finished.getResultCode());
        assertEquals("secret output", finished.getResultMessage());
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
    public void duplicateEnvironmentResultIgnoresJsonObjectFieldOrder() throws Exception {
        ProcessingAgentCommandRecord completed = leasedCommand("SUCCEEDED");
        completed.setCommandType("START_TRAINING_ENVIRONMENT");
        completed.setResultCode("ENVIRONMENT_STARTED");
        completed.setResultMessage("environment operation completed");
        completed.setResultJson("{\"pair\":{\"annotation\":{\"state\":\"RUNNING\"},"
                + "\"editor\":{\"state\":\"RUNNING\"}},\"sentinelPresent\":true}");
        completed.setCompletedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(mapper.selectById(completed.getCommandId())).thenReturn(completed);
        AgentCommandResultRequest replay = result(true, "ENVIRONMENT_STARTED",
                "environment operation completed");
        replay.setDetails(new ObjectMapper().readTree("{\"sentinelPresent\":true,\"pair\":{"
                + "\"editor\":{\"state\":\"RUNNING\"},"
                + "\"annotation\":{\"state\":\"RUNNING\"}}}"));

        AgentCommandView view = service.finish(agent, completed.getCommandId(), replay);

        assertEquals("SUCCEEDED", view.getState());
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

    private ProcessingAgentCommandRecord runningTerminalLease(String sessionId) {
        ProcessingAgentCommandRecord record = command("RUNNING");
        record.setCommandType("OPEN_ROOT_TERMINAL");
        record.setLeaseToken("lease");
        record.setPayloadJson("{\"sessionId\":\"" + sessionId + "\"}");
        return record;
    }

    private void stubRunningTerminalLease(ProcessingAgentCommandRecord record, String requestedSessionId) {
        when(mapper.selectRunningTerminalLeaseForUpdate(record.getCommandId(),
                agent.getAgentId(), "lease", requestedSessionId)).thenReturn(record);
    }

    private ProcessingAgentCommandRecord activeTerminalCommand(String sessionId, String payload) {
        ProcessingAgentCommandRecord record = command("PENDING");
        record.setCommandType("OPEN_ROOT_TERMINAL");
        record.setActiveDedupKey(agent.getAgentId() + ":OPEN_ROOT_TERMINAL");
        record.setPayloadJson(payload);
        return record;
    }

    private void stubActiveTerminal(ProcessingAgentCommandRecord record) {
        when(mapper.selectActiveByDedup(agent.getAgentId(), "OPEN_ROOT_TERMINAL",
                agent.getAgentId() + ":OPEN_ROOT_TERMINAL")).thenReturn(record);
    }

    private AgentCommandView requestTerminal(String sessionId) {
        return service.requestTerminalCommand(agent, sessionId, NOW.plusSeconds(90),
                NOW.plusSeconds(7200), 7, "SUPER_ADMIN");
    }

    private String terminalPayload(String sessionId) {
        return "{\"sessionId\":\"" + sessionId + "\","
                + "\"relayUrl\":\"wss://127.0.0.1:19147/terminal/v1/agent/" + sessionId + "\","
                + "\"agentConnectionDeadline\":\"" + NOW.plusSeconds(90) + "\","
                + "\"idleTimeoutSeconds\":600,"
                + "\"absoluteExpiresAt\":\"" + NOW.plusSeconds(7200) + "\"}";
    }

    private void expectTerminalSessionConflict(Runnable action) {
        try {
            action.run();
        } catch (AgentProtocolException exception) {
            assertEquals("TERMINAL_COMMAND_SESSION_CONFLICT", exception.getCode());
            assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
            return;
        }
        throw new AssertionError("expected terminal command session conflict");
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

    private AgentCommandService terminalService(String relayUrl, boolean localDevelopment) {
        return new AgentCommandService(mapper, new ObjectMapper().findAndRegisterModules(),
                audit, Clock.fixed(NOW, ZoneOffset.UTC), null, relayUrl, localDevelopment);
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
