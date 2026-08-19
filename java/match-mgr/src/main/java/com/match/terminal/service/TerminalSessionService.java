package com.match.terminal.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.agent.web.AgentProtocolException;
import com.match.entity.User;
import com.match.security.UserRole;
import com.match.terminal.model.TerminalSessionView;
import com.match.terminal.persistence.TerminalSessionMapper;
import com.match.terminal.persistence.TerminalSessionRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TerminalSessionService {
    private static final String CONFIRMATION = "OPEN_ROOT_TERMINAL";
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(15);
    private static final Duration AGENT_CONNECTION_WINDOW = Duration.ofSeconds(90);
    private static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(2);

    private final TerminalSessionMapper sessionMapper;
    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final Clock clock;

    public TerminalSessionService(TerminalSessionMapper sessionMapper, ProcessingAgentMapper agentMapper,
                                  AgentCommandService commandService, Clock clock) {
        this.sessionMapper = sessionMapper;
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.clock = clock;
    }

    @Transactional
    public TerminalSessionView create(String agentId, User actor, String confirmation) {
        if (!CONFIRMATION.equals(confirmation)) {
            throw error("TERMINAL_CONFIRMATION_REQUIRED",
                    "Exact root terminal confirmation is required");
        }
        if (!isSuperAdmin(actor)) {
            throw error("TERMINAL_SUPER_ADMIN_REQUIRED",
                    "Only a super administrator can open a root terminal");
        }

        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw error("TERMINAL_AGENT_UNAVAILABLE", "Processing Agent is unavailable");
        }
        Instant now = clock.instant();
        if (!isOnline(agent, now)) {
            throw error("TERMINAL_AGENT_OFFLINE", "Processing Agent is offline");
        }

        Instant agentConnectionDeadline = now.plus(AGENT_CONNECTION_WINDOW);
        Instant absoluteExpiresAt = now.plus(ABSOLUTE_LIFETIME);
        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        TerminalSessionRecord record = new TerminalSessionRecord();
        record.setSessionId(UUID.randomUUID().toString());
        record.setAgentId(agent.getAgentId());
        record.setRequesterUserId(actor.getUserId());
        record.setRequesterRole(UserRole.SUPER_ADMIN.name());
        record.setState("WAITING_AGENT");
        record.setActiveAgentId(agent.getAgentId());
        record.setRequestedAt(utcNow);
        record.setAbsoluteExpiresAt(LocalDateTime.ofInstant(absoluteExpiresAt, ZoneOffset.UTC));
        record.setUpdatedAt(utcNow);
        try {
            sessionMapper.insert(record);
        } catch (DuplicateKeyException collision) {
            throw error("TERMINAL_SESSION_ACTIVE",
                    "An active terminal session already exists for this Agent");
        }

        AgentCommandView command;
        try {
            command = commandService.requestTerminalCommand(agent, record.getSessionId(),
                    agentConnectionDeadline, absoluteExpiresAt, actor.getUserId(),
                    UserRole.SUPER_ADMIN.name());
        } catch (AgentProtocolException exception) {
            if (!"TERMINAL_COMMAND_SESSION_CONFLICT".equals(exception.getCode())) {
                throw exception;
            }
            throw new TerminalSessionException(exception.getCode(), exception.getMessage(),
                    HttpStatus.CONFLICT);
        }
        if (sessionMapper.setCommand(record.getSessionId(), command.getCommandId(), utcNow) != 1) {
            throw error("TERMINAL_COMMAND_ATTACH_FAILED",
                    "Terminal command could not be attached to its session");
        }

        TerminalSessionView view = new TerminalSessionView();
        view.setSessionId(record.getSessionId());
        view.setAgentId(record.getAgentId());
        view.setState(record.getState());
        view.setRequestedAt(now);
        view.setAgentConnectionDeadline(agentConnectionDeadline);
        view.setAbsoluteExpiresAt(absoluteExpiresAt);
        view.setCommandId(command.getCommandId());
        return view;
    }

    private boolean isSuperAdmin(User actor) {
        if (actor == null || actor.getUserId() == null) {
            return false;
        }
        try {
            return UserRole.resolve(actor.getRole(), actor.getIsAdmin(), actor.getUserName())
                    == UserRole.SUPER_ADMIN;
        } catch (RuntimeException invalidRole) {
            return false;
        }
    }

    private boolean isOnline(ProcessingAgentRecord agent, Instant now) {
        if (agent.getLastSeenAt() == null) {
            return false;
        }
        Instant lastSeen = agent.getLastSeenAt().toInstant(ZoneOffset.UTC);
        return !lastSeen.isBefore(now.minus(ONLINE_WINDOW)) && !lastSeen.isAfter(now);
    }

    private TerminalSessionException error(String code, String message) {
        return new TerminalSessionException(code, message);
    }
}
