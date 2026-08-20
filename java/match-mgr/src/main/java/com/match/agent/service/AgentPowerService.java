package com.match.agent.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.entity.User;
import com.match.licensing.guard.LicenseGuard;
import com.match.licensing.guard.LicenseAccessException;
import com.match.security.UserRole;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AgentPowerService {
    private static final long ONLINE_SECONDS = 15;

    private final ProcessingAgentMapper agentMapper;
    private final AgentCommandService commandService;
    private final LicenseGuard licenseGuard;
    private final WakeOnLanSender wakeOnLanSender;
    private final AgentAuditService auditService;
    private final Clock clock;

    public AgentPowerService(ProcessingAgentMapper agentMapper, AgentCommandService commandService,
                             LicenseGuard licenseGuard, WakeOnLanSender wakeOnLanSender,
                             AgentAuditService auditService, Clock clock) {
        this.agentMapper = agentMapper;
        this.commandService = commandService;
        this.licenseGuard = licenseGuard;
        this.wakeOnLanSender = wakeOnLanSender;
        this.auditService = auditService;
        this.clock = clock;
    }

    public void wake(String agentId, User actor, UserRole role) {
        Integer userId = actorId(actor);
        try {
            requirePowerRole(role);
            ProcessingAgentRecord agent = requireManagedAgent(agentId);
            requireLicenseUnlessMaintenance(role);
            wakeOnLanSender.send(agent.getMacAddress());
            auditService.recordSuccess("WAKE_PACKET_SENT", userId, agentId, null);
        } catch (RuntimeException exception) {
            auditService.recordFailure("WAKE_PACKET_SEND", failureCode(exception, "WAKE_PACKET_FAILED"),
                    userId, agentId, null);
            throw exception;
        }
    }

    public AgentCommandView shutdown(String agentId, User actor, UserRole role) {
        Integer userId = actorId(actor);
        try {
            requirePowerRole(role);
            ProcessingAgentRecord agent = requireManagedAgent(agentId);
            requireLicenseUnlessMaintenance(role);
            LocalDateTime onlineAfter = LocalDateTime.ofInstant(
                    clock.instant().minusSeconds(ONLINE_SECONDS), ZoneOffset.UTC);
            if (agent.getLastSeenAt() == null || agent.getLastSeenAt().isBefore(onlineAfter)) {
                throw new IllegalArgumentException("Processing server is offline; use Wake-on-LAN first");
            }
            AgentCommandView command = commandService.requestShutdown(agent, userId, role.name());
            auditService.recordSuccess("SHUTDOWN_REQUESTED", userId, agentId, command.getCommandId());
            return command;
        } catch (RuntimeException exception) {
            auditService.recordFailure("SHUTDOWN_REQUEST", failureCode(exception, "POWER_OPERATION_REJECTED"),
                    userId, agentId, null);
            throw exception;
        }
    }

    private ProcessingAgentRecord requireManagedAgent(String agentId) {
        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("Processing server does not exist");
        }
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw new IllegalArgumentException("Processing server is disabled");
        }
        return agent;
    }

    private void requirePowerRole(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Only administrators may control server power");
        }
    }

    private void requireLicenseUnlessMaintenance(UserRole role) {
        if (role != UserRole.SUPER_ADMIN) {
            licenseGuard.requireActive();
        }
    }

    private Integer actorId(User actor) {
        if (actor == null || actor.getUserId() == null) {
            throw new IllegalArgumentException("Power operation actor is required");
        }
        return actor.getUserId();
    }

    private String failureCode(RuntimeException exception, String fallback) {
        if (exception instanceof LicenseAccessException) {
            return ((LicenseAccessException) exception).getReasonCode();
        }
        if (exception instanceof IllegalArgumentException) {
            return "POWER_OPERATION_REJECTED";
        }
        return fallback;
    }
}
