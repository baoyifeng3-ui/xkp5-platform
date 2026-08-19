package com.match.terminal.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Before
    public void setUp() {
        sessions = mock(TerminalSessionMapper.class);
        agents = mock(ProcessingAgentMapper.class);
        commands = mock(AgentCommandService.class);
        service = new TerminalSessionService(sessions, agents, commands,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        expectCode("TERMINAL_SUPER_ADMIN_REQUIRED",
                () -> service.create(AGENT_ID, user(8, "admin", "ADMIN", true), "OPEN_ROOT_TERMINAL"));
        expectCode("TERMINAL_SUPER_ADMIN_REQUIRED",
                () -> service.create(AGENT_ID, user(9, "user", "USER", false), "OPEN_ROOT_TERMINAL"));
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
}
