package com.match.terminal.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.agent.web.AgentProtocolException;
import com.match.entity.User;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.model.TerminalTicketView;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TerminalSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final String AGENT_ID = "11111111-1111-4111-8111-111111111111";
    private TerminalSessionMapper sessions;
    private ProcessingAgentMapper agents;
    private AgentCommandService commands;
    private TerminalSessionService service;
    private ProcessingAgentRecord agent;
    private User actor;
    private SecureRandom random;

    @Before
    public void setUp() {
        sessions = mock(TerminalSessionMapper.class);
        agents = mock(ProcessingAgentMapper.class);
        commands = mock(AgentCommandService.class);
        random = new DeterministicSecureRandom();
        service = new TerminalSessionService(sessions, agents, commands,
                Clock.fixed(NOW, ZoneOffset.UTC), random);
        agent = new ProcessingAgentRecord();
        agent.setAgentId(AGENT_ID);
        agent.setEnabled(true);
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC));
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);
        actor = user(7, "root-admin", "SUPER_ADMIN", true);
        AgentCommandView command = new AgentCommandView();
        command.setCommandId("22222222-2222-4222-8222-222222222222");
        when(commands.requestTerminalCommand(eq(agent), any(String.class),
                eq(NOW.plusSeconds(90)), eq(NOW.plusSeconds(7200)), eq(7), eq("SUPER_ADMIN")))
                .thenReturn(command);
        when(sessions.setCommand(any(String.class), eq(command.getCommandId()),
                eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))).thenReturn(1);
    }

    @Test
    public void createsWaitingSessionAndQueuesCommandAtInclusiveOnlineBoundary() {
        TerminalSessionView created = service.create(AGENT_ID, actor, "OPEN_ROOT_TERMINAL");

        assertEquals("WAITING_AGENT", created.getState());
        assertEquals(NOW, created.getRequestedAt());
        assertEquals(NOW.plusSeconds(90), created.getAgentConnectionDeadline());
        assertEquals(NOW.plusSeconds(7200), created.getAbsoluteExpiresAt());
        assertEquals("22222222-2222-4222-8222-222222222222", created.getCommandId());
        assertNotNull(created.getSessionId());
        ArgumentCaptor<TerminalSessionRecord> saved = ArgumentCaptor.forClass(TerminalSessionRecord.class);
        verify(sessions).insert(saved.capture());
        TerminalSessionRecord record = saved.getValue();
        assertEquals(AGENT_ID, record.getAgentId());
        assertEquals(AGENT_ID, record.getActiveAgentId());
        assertEquals(Integer.valueOf(7), record.getRequesterUserId());
        assertEquals("SUPER_ADMIN", record.getRequesterRole());
        verify(commands).requestTerminalCommand(eq(agent), eq(created.getSessionId()),
                eq(created.getAgentConnectionDeadline()), eq(created.getAbsoluteExpiresAt()),
                eq(actor.getUserId()), eq("SUPER_ADMIN"));
        verify(sessions).setCommand(created.getSessionId(), created.getCommandId(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    public void exactConfirmationIsRequired() {
        expectCode("TERMINAL_CONFIRMATION_REQUIRED",
                () -> service.create(AGENT_ID, actor, "open_root_terminal"));
        verify(agents, never()).selectForManagement(any(String.class));
    }

    @Test
    public void rejectsAdminAndUserActors() {
        for (User forbidden : new User[]{user(8, "admin", "ADMIN", true),
                user(9, "user", "USER", false)}) {
            TerminalSessionException exception = expectCode("TERMINAL_SUPER_ADMIN_REQUIRED",
                    () -> service.create(AGENT_ID, forbidden, "wrong-confirmation"));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        }
        verify(agents, never()).selectForManagement(any(String.class));
    }

    @Test
    public void rejectsMissingDisabledRemovedStaleAndFutureAgents() {
        when(agents.selectForManagement(AGENT_ID)).thenReturn(null);
        expectCode("TERMINAL_AGENT_UNAVAILABLE", () -> create());
        agent.setEnabled(false);
        when(agents.selectForManagement(AGENT_ID)).thenReturn(agent);
        expectCode("TERMINAL_AGENT_UNAVAILABLE", () -> create());
        agent.setEnabled(true);
        agent.setRemovedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        expectCode("TERMINAL_AGENT_UNAVAILABLE", () -> create());
        agent.setRemovedAt(null);
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.minusSeconds(15).minusNanos(1), ZoneOffset.UTC));
        expectCode("TERMINAL_AGENT_OFFLINE", () -> create());
        agent.setLastSeenAt(LocalDateTime.ofInstant(NOW.plusNanos(1), ZoneOffset.UTC));
        expectCode("TERMINAL_AGENT_OFFLINE", () -> create());
    }

    @Test
    public void duplicateActiveSessionHasStableConflictAndDoesNotReturnExistingSession() {
        doThrow(new DuplicateKeyException("uk_terminal_active_agent"))
                .when(sessions).insert(any(TerminalSessionRecord.class));

        TerminalSessionException exception = expectCode("TERMINAL_SESSION_ACTIVE", () -> create());

        assertEquals("An active terminal session already exists for this Agent", exception.getMessage());
        verify(commands, never()).requestTerminalCommand(any(ProcessingAgentRecord.class), any(String.class),
                any(Instant.class), any(Instant.class), any(Integer.class), any(String.class));
    }

    @Test
    public void commandAndAttachmentFailuresEscapeTransactionalMethod() throws Exception {
        Method create = TerminalSessionService.class.getMethod("create", String.class, User.class, String.class);
        Transactional transactional = create.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertTrue(transactional.rollbackFor().length == 0);

        RuntimeException commandFailure = new RuntimeException("command insert failed");
        when(commands.requestTerminalCommand(eq(agent), any(String.class), any(Instant.class),
                any(Instant.class), eq(7), eq("SUPER_ADMIN"))).thenThrow(commandFailure);
        boolean commandFailed = false;
        try {
            create();
        } catch (RuntimeException actual) {
            assertEquals(commandFailure, actual);
            commandFailed = true;
        }
        assertTrue(commandFailed);

        AgentCommandView command = new AgentCommandView();
        command.setCommandId("22222222-2222-4222-8222-222222222222");
        when(commands.requestTerminalCommand(eq(agent), any(String.class), any(Instant.class),
                any(Instant.class), eq(7), eq("SUPER_ADMIN"))).thenReturn(command);
        when(sessions.setCommand(any(String.class), eq(command.getCommandId()), any(LocalDateTime.class)))
                .thenReturn(0);
        expectCode("TERMINAL_COMMAND_ATTACH_FAILED", () -> create());
    }

    @Test
    public void translatesOnlyTerminalCommandSessionConflictsToTerminalDomain() {
        AgentProtocolException conflict = new AgentProtocolException(
                "TERMINAL_COMMAND_SESSION_CONFLICT", "foreign terminal command", HttpStatus.CONFLICT);
        when(commands.requestTerminalCommand(eq(agent), any(String.class), any(Instant.class),
                any(Instant.class), eq(7), eq("SUPER_ADMIN"))).thenThrow(conflict);

        TerminalSessionException translated = expectCode(
                "TERMINAL_COMMAND_SESSION_CONFLICT", () -> create());

        assertEquals(HttpStatus.CONFLICT, translated.getStatus());

        AgentProtocolException unrelated = new AgentProtocolException(
                "COMMAND_LEASE_CONFLICT", "unrelated", HttpStatus.CONFLICT);
        when(commands.requestTerminalCommand(eq(agent), any(String.class), any(Instant.class),
                any(Instant.class), eq(7), eq("SUPER_ADMIN"))).thenThrow(unrelated);
        try {
            create();
        } catch (AgentProtocolException actual) {
            assertEquals(unrelated, actual);
            return;
        }
        throw new AssertionError("unrelated Agent protocol failure was translated");
    }

    @Test
    public void issuesDigestOnlyAgentTicketCappedByAgentDeadline() {
        TerminalSessionRecord record = waitingAgent(NOW.minusSeconds(70), NOW.plusSeconds(100));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(commands.hasRunningTerminalLease(record.getCommandId(), AGENT_ID, "lease", record.getSessionId()))
                .thenReturn(true);
        when(sessions.issueAgentTicket(eq(record.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(20))), eq(utc(NOW)))).thenReturn(1);

        TerminalTicketView issued = service.issueAgentTicket(agent, record.getSessionId(),
                record.getCommandId(), "lease");

        assertEquals(43, issued.getTicket().length());
        assertTrue(Pattern.matches("[A-Za-z0-9_-]{43}", issued.getTicket()));
        assertEquals(NOW.plusSeconds(20), issued.getExpiresAt());
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(sessions).issueAgentTicket(eq(record.getSessionId()), digest.capture(),
                eq(utc(NOW.plusSeconds(20))), eq(utc(NOW)));
        assertTrue(Pattern.matches("[0-9a-f]{64}", digest.getValue()));
        assertFalse(digest.getValue().contains(issued.getTicket()));
    }

    @Test
    public void refusesExpiredAgentPhaseAndWrongRelationshipOrLease() {
        TerminalSessionRecord record = waitingAgent(NOW.minusSeconds(90), NOW.plusSeconds(100));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                agent, record.getSessionId(), record.getCommandId(), "lease"));

        record.setRequestedAt(utc(NOW.minusSeconds(1)));
        ProcessingAgentRecord other = new ProcessingAgentRecord();
        other.setAgentId("other-agent");
        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                other, record.getSessionId(), record.getCommandId(), "lease"));
        when(commands.hasRunningTerminalLease(record.getCommandId(), AGENT_ID, "lease", record.getSessionId()))
                .thenReturn(false);
        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                agent, record.getSessionId(), record.getCommandId(), "lease"));
        verify(sessions, never()).issueAgentTicket(any(String.class), any(String.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void consumesAgentTicketAtomicallyAndRequiresAuthenticatedRelationship() {
        TerminalSessionRecord record = waitingAgent(NOW.minusSeconds(1), NOW.plusSeconds(100));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(sessions.consumeAgentTicket(eq(record.getSessionId()), eq(AGENT_ID),
                any(String.class), eq(utc(NOW))))
                .thenReturn(1, 0);

        assertTrue(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        ProcessingAgentRecord other = new ProcessingAgentRecord();
        other.setAgentId("other-agent");
        assertFalse(service.consumeAgentTicket(other, record.getSessionId(), "secret"));
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(sessions, org.mockito.Mockito.times(2)).consumeAgentTicket(
                eq(record.getSessionId()), eq(AGENT_ID), digest.capture(), eq(utc(NOW)));
        assertTrue(Pattern.matches("[0-9a-f]{64}", digest.getValue()));
    }

    @Test
    public void agentTicketConsumeRequiresInclusiveOnlineEnabledAgentWindow() {
        TerminalSessionRecord record = waitingAgent(NOW.minusSeconds(1), NOW.plusSeconds(100));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);

        agent.setLastSeenAt(null);
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        agent.setLastSeenAt(utc(NOW.minusSeconds(15).minusNanos(1)));
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        agent.setLastSeenAt(utc(NOW.plusNanos(1)));
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        agent.setLastSeenAt(utc(NOW.minusSeconds(15)));
        agent.setEnabled(false);
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        agent.setEnabled(true);
        agent.setRemovedAt(utc(NOW.minusSeconds(1)));
        assertFalse(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
        verify(sessions, never()).consumeAgentTicket(any(String.class), any(String.class),
                any(String.class), any(LocalDateTime.class));

        agent.setRemovedAt(null);
        agent.setLastSeenAt(utc(NOW));
        when(sessions.consumeAgentTicket(eq(record.getSessionId()), eq(AGENT_ID),
                any(String.class), eq(utc(NOW)))).thenReturn(1);
        assertTrue(service.consumeAgentTicket(agent, record.getSessionId(), "secret"));
    }

    @Test
    public void relayAttachmentEligibilityRequiresConsumedRoleAndNonterminalState() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        record.setAgentTicketConsumedAt(utc(NOW.minusSeconds(1)));
        record.setBrowserTicketConsumedAt(utc(NOW));
        record.setBrowserConnectedAt(utc(NOW));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);

        assertTrue(service.isRelayAttachmentEligible(record.getSessionId(), "AGENT", AGENT_ID));
        assertTrue(service.isRelayAttachmentEligible(record.getSessionId(), "BROWSER", null));
        assertFalse(service.isRelayAttachmentEligible(record.getSessionId(), "AGENT", "other-agent"));

        record.setState("CLOSED");
        assertFalse(service.isRelayAttachmentEligible(record.getSessionId(), "BROWSER", null));
        record.setState("WAITING_BROWSER");
        record.setAgentConnectedAt(utc(NOW.minusSeconds(60)));
        assertFalse(service.isRelayAttachmentEligible(record.getSessionId(), "BROWSER", null));
    }

    @Test
    public void operatorCloseRunsPersistenceInsideRelayLifecycleBoundary() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(sessions.close(record.getSessionId(), "CLOSED", "OPERATOR_CLOSED",
                "Terminal session closed by operator", utc(NOW))).thenReturn(1);
        TerminalRelayLifecycle lifecycle = mock(TerminalRelayLifecycle.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(lifecycle).closePersistedSession(eq(record.getSessionId()), any(Runnable.class));
        TerminalSessionService bounded = new TerminalSessionService(sessions, agents, commands,
                Clock.fixed(NOW, ZoneOffset.UTC), random, lifecycle);

        bounded.close(record.getSessionId(), actor);

        verify(lifecycle).closePersistedSession(eq(record.getSessionId()), any(Runnable.class));
        verify(sessions).close(record.getSessionId(), "CLOSED", "OPERATOR_CLOSED",
                "Terminal session closed by operator", utc(NOW));
    }

    @Test
    public void springLifecycleProviderIsResolvedLazilyToAvoidBeanCycle() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TerminalRelayLifecycle> provider = mock(ObjectProvider.class);

        new TerminalSessionService(sessions, agents, commands,
                Clock.fixed(NOW, ZoneOffset.UTC), provider);

        verify(provider, never()).getIfAvailable();
    }

    @Test
    public void agentIssuanceRejectsWrongCommandAndDelegatedLeaseValidationFailure() {
        TerminalSessionRecord record = waitingAgent(NOW.minusSeconds(1), NOW.plusSeconds(100));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);

        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                agent, record.getSessionId(), "wrong-command", "lease"));
        when(commands.hasRunningTerminalLease(record.getCommandId(), AGENT_ID,
                "lease", record.getSessionId())).thenReturn(false);
        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                agent, record.getSessionId(), record.getCommandId(), "lease"));
        record.setState("WAITING_BROWSER");
        expectCode("TERMINAL_TICKET_UNAVAILABLE", () -> service.issueAgentTicket(
                agent, record.getSessionId(), record.getCommandId(), "lease"));
        verify(sessions, never()).issueAgentTicket(any(String.class), any(String.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void rotatesBrowserTicketAndCapsExpiryByBrowserDeadline() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(45), NOW.plusSeconds(120));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(sessions.issueBrowserTicket(eq(record.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(15))), eq(utc(NOW)))).thenReturn(1);

        TerminalTicketView first = service.issueBrowserTicket(record.getSessionId(), actor);
        TerminalTicketView second = service.issueBrowserTicket(record.getSessionId(), actor);

        assertEquals(NOW.plusSeconds(15), first.getExpiresAt());
        assertEquals(43, second.getTicket().length());
        verify(sessions, org.mockito.Mockito.times(2)).issueBrowserTicket(eq(record.getSessionId()),
                any(String.class), eq(utc(NOW.plusSeconds(15))), eq(utc(NOW)));
    }

    @Test
    public void consumedBrowserTicketCannotBeRotatedBeforeActivation() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        record.setBrowserTicketExpiresAt(utc(NOW.plusSeconds(10)));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(sessions.consumeBrowserTicket(eq(record.getSessionId()), any(String.class), eq(utc(NOW))))
                .thenAnswer(invocation -> {
                    record.setBrowserTicketConsumedAt(utc(NOW));
                    record.setBrowserConnectedAt(utc(NOW));
                    return 1;
                });

        assertTrue(service.consumeBrowserTicket(record.getSessionId(), "ticket"));
        expectCode("TERMINAL_TICKET_UNAVAILABLE",
                () -> service.issueBrowserTicket(record.getSessionId(), actor));

        verify(sessions, never()).issueBrowserTicket(any(String.class), any(String.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void browserConnectionWithoutConsumedTimestampAlsoPreventsRotation() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        record.setBrowserConnectedAt(utc(NOW));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);

        expectCode("TERMINAL_TICKET_UNAVAILABLE",
                () -> service.issueBrowserTicket(record.getSessionId(), actor));

        verify(sessions, never()).issueBrowserTicket(any(String.class), any(String.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void consumesValidBrowserTicketOnceAndReplayOrInvalidTicketReturnsFalse() {
        String sessionId = "33333333-3333-4333-8333-333333333333";
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        record.setBrowserTicketExpiresAt(utc(NOW.plusSeconds(10)));
        when(sessions.selectById(sessionId)).thenReturn(record);
        when(sessions.consumeBrowserTicket(eq(sessionId), any(String.class), eq(utc(NOW))))
                .thenReturn(1, 0, 0);

        assertTrue(service.consumeBrowserTicket(sessionId, "ticket"));
        assertFalse(service.consumeBrowserTicket(sessionId, "ticket"));
        assertFalse(service.consumeBrowserTicket(sessionId, "invalid-ticket"));
    }

    @Test
    public void browserTicketAtExpiryBoundaryIsRejectedBeforeAtomicConsume() {
        String sessionId = "33333333-3333-4333-8333-333333333333";
        TerminalSessionRecord expired = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        expired.setBrowserTicketExpiresAt(utc(NOW));
        when(sessions.selectById(sessionId)).thenReturn(expired);

        assertFalse(service.consumeBrowserTicket(sessionId, "ticket"));

        verify(sessions, never()).consumeBrowserTicket(any(String.class), any(String.class),
                any(LocalDateTime.class));
    }

    @Test
    public void browserTicketCannotBeConsumedAgainstForeignSession() {
        when(sessions.selectById("foreign-session")).thenReturn(null);

        assertFalse(service.consumeBrowserTicket("foreign-session", "ticket"));

        verify(sessions, never()).consumeBrowserTicket(any(String.class), any(String.class),
                any(LocalDateTime.class));
    }

    @Test
    public void relayHooksPersistOnlyStateAndBoundedByteCounts() {
        String sessionId = "33333333-3333-4333-8333-333333333333";
        when(sessions.markActive(sessionId, utc(NOW))).thenReturn(1);
        when(sessions.setTrafficTotals(sessionId, 4096L, 2048L, utc(NOW))).thenReturn(1);

        assertTrue(service.markRelayActive(sessionId));
        assertTrue(service.recordRelayTraffic(sessionId, 4096L, 2048L));
        assertFalse(service.recordRelayTraffic(sessionId, 0L, 0L));
        service.finishRelay(sessionId, false, "PROTOCOL_ERROR");
        service.finishRelay(sessionId, true, "ignored");
        service.finishRelay(sessionId, false, "externally-controlled-unbounded-reason");

        verify(sessions).setTrafficTotals(sessionId, 4096L, 2048L, utc(NOW));
        verify(sessions).close(sessionId, "FAILED", "PROTOCOL_ERROR",
                "Terminal relay closed", utc(NOW));
        verify(sessions).close(sessionId, "CLOSED", "OPERATOR_CLOSED",
                "Terminal session closed by operator", utc(NOW));
        verify(sessions).close(sessionId, "FAILED", "RELAY_FAILURE",
                "Terminal relay closed", utc(NOW));
    }

    @Test
    public void capsBothTicketTypesByAbsoluteExpiry() {
        TerminalSessionRecord agentRecord = waitingAgent(NOW.minusSeconds(1), NOW.plusSeconds(5));
        when(sessions.selectById(agentRecord.getSessionId())).thenReturn(agentRecord);
        when(commands.hasRunningTerminalLease(agentRecord.getCommandId(), AGENT_ID, "lease",
                agentRecord.getSessionId())).thenReturn(true);
        when(sessions.issueAgentTicket(eq(agentRecord.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(5))), eq(utc(NOW)))).thenReturn(1);
        assertEquals(NOW.plusSeconds(5), service.issueAgentTicket(agent, agentRecord.getSessionId(),
                agentRecord.getCommandId(), "lease").getExpiresAt());

        TerminalSessionRecord browserRecord = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(4));
        when(sessions.selectById(browserRecord.getSessionId())).thenReturn(browserRecord);
        when(sessions.issueBrowserTicket(eq(browserRecord.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(4))), eq(utc(NOW)))).thenReturn(1);
        assertEquals(NOW.plusSeconds(4),
                service.issueBrowserTicket(browserRecord.getSessionId(), actor).getExpiresAt());
    }

    @Test
    public void usesNominalSixtySecondTtlWhenBothPhaseAndAbsoluteDeadlinesAllowIt() {
        TerminalSessionRecord agentRecord = waitingAgent(NOW, NOW.plusSeconds(300));
        when(sessions.selectById(agentRecord.getSessionId())).thenReturn(agentRecord);
        when(commands.hasRunningTerminalLease(agentRecord.getCommandId(), AGENT_ID, "lease",
                agentRecord.getSessionId())).thenReturn(true);
        when(sessions.issueAgentTicket(eq(agentRecord.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(60))), eq(utc(NOW)))).thenReturn(1);
        assertEquals(NOW.plusSeconds(60), service.issueAgentTicket(agent, agentRecord.getSessionId(),
                agentRecord.getCommandId(), "lease").getExpiresAt());

        TerminalSessionRecord browserRecord = waitingBrowser(NOW, NOW.plusSeconds(300));
        when(sessions.selectById(browserRecord.getSessionId())).thenReturn(browserRecord);
        when(sessions.issueBrowserTicket(eq(browserRecord.getSessionId()), any(String.class),
                eq(utc(NOW.plusSeconds(60))), eq(utc(NOW)))).thenReturn(1);
        assertEquals(NOW.plusSeconds(60),
                service.issueBrowserTicket(browserRecord.getSessionId(), actor).getExpiresAt());
    }

    @Test
    public void refusesBrowserTicketAtPhaseOrAbsoluteDeadline() {
        TerminalSessionRecord phaseExpired = waitingBrowser(NOW.minusSeconds(60), NOW.plusSeconds(60));
        when(sessions.selectById(phaseExpired.getSessionId())).thenReturn(phaseExpired);
        expectCode("TERMINAL_TICKET_UNAVAILABLE",
                () -> service.issueBrowserTicket(phaseExpired.getSessionId(), actor));

        phaseExpired.setAgentConnectedAt(utc(NOW));
        phaseExpired.setAbsoluteExpiresAt(utc(NOW));
        expectCode("TERMINAL_TICKET_UNAVAILABLE",
                () -> service.issueBrowserTicket(phaseExpired.getSessionId(), actor));
        verify(sessions, never()).issueBrowserTicket(any(String.class), any(String.class),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void operatorApisIndependentlyRequireSuperAdminAndCloseIsIdempotent() {
        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        when(sessions.selectById(record.getSessionId())).thenReturn(record);
        when(sessions.close(record.getSessionId(), "CLOSED", "OPERATOR_CLOSED",
                "Terminal session closed by operator", utc(NOW))).thenReturn(1);
        User admin = user(8, "admin", "ADMIN", true);
        User user = user(9, "user", "USER", false);

        for (User forbidden : new User[]{admin, user}) {
            assertForbidden(() -> service.view(record.getSessionId(), forbidden));
            assertForbidden(() -> service.issueBrowserTicket(record.getSessionId(), forbidden));
            assertForbidden(() -> service.close(record.getSessionId(), forbidden));
        }
        assertEquals(record.getSessionId(), service.view(record.getSessionId(), actor).getSessionId());
        service.close(record.getSessionId(), actor);
        record.setState("CLOSED");
        service.close(record.getSessionId(), actor);
        verify(sessions, org.mockito.Mockito.times(1)).close(record.getSessionId(), "CLOSED",
                "OPERATOR_CLOSED", "Terminal session closed by operator", utc(NOW));
    }

    @Test
    public void missingCloseFailsNotFoundAndConcurrentForeignStateFailsSafely() {
        when(sessions.selectById("missing")).thenReturn(null);
        TerminalSessionException missing = expectCode("TERMINAL_SESSION_NOT_FOUND",
                () -> service.close("missing", actor));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());

        TerminalSessionRecord record = waitingBrowser(NOW.minusSeconds(1), NOW.plusSeconds(120));
        when(sessions.selectById(record.getSessionId())).thenReturn(record, record);
        when(sessions.close(eq(record.getSessionId()), eq("CLOSED"), any(String.class),
                any(String.class), eq(utc(NOW)))).thenReturn(0);
        expectCode("TERMINAL_SESSION_CLOSE_FAILED", () -> service.close(record.getSessionId(), actor));
    }

    private TerminalSessionRecord waitingAgent(Instant requestedAt, Instant absoluteExpiry) {
        TerminalSessionRecord record = baseRecord("WAITING_AGENT", absoluteExpiry);
        record.setRequestedAt(utc(requestedAt));
        return record;
    }

    private TerminalSessionRecord waitingBrowser(Instant agentConnectedAt, Instant absoluteExpiry) {
        TerminalSessionRecord record = baseRecord("WAITING_BROWSER", absoluteExpiry);
        record.setRequestedAt(utc(NOW.minusSeconds(30)));
        record.setAgentConnectedAt(utc(agentConnectedAt));
        return record;
    }

    private TerminalSessionRecord baseRecord(String state, Instant absoluteExpiry) {
        TerminalSessionRecord record = new TerminalSessionRecord();
        record.setSessionId("33333333-3333-4333-8333-333333333333");
        record.setAgentId(AGENT_ID);
        record.setActiveAgentId(AGENT_ID);
        record.setRequesterUserId(actor.getUserId());
        record.setRequesterRole("SUPER_ADMIN");
        record.setState(state);
        record.setCommandId("22222222-2222-4222-8222-222222222222");
        record.setAbsoluteExpiresAt(utc(absoluteExpiry));
        return record;
    }

    private LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private TerminalSessionView create() {
        return service.create(AGENT_ID, actor, "OPEN_ROOT_TERMINAL");
    }

    private User user(int id, String name, String role, boolean legacyAdmin) {
        User value = new User();
        value.setUserId(id);
        value.setUserName(name);
        value.setRole(role);
        value.setIsAdmin(legacyAdmin);
        return value;
    }

    private TerminalSessionException expectCode(String code, Runnable action) {
        try {
            action.run();
        } catch (TerminalSessionException exception) {
            assertEquals(code, exception.getCode());
            return exception;
        }
        throw new AssertionError("expected terminal session exception " + code);
    }

    private void assertForbidden(Runnable action) {
        assertEquals(HttpStatus.FORBIDDEN,
                expectCode("TERMINAL_SUPER_ADMIN_REQUIRED", action).getStatus());
    }

    private static class DeterministicSecureRandom extends SecureRandom {
        private int sequence;

        @Override
        public void nextBytes(byte[] bytes) {
            sequence++;
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (sequence + i);
            }
        }
    }
}
