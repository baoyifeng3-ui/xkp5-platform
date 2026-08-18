package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AgentAdministrationService {
    private final ProcessingAgentMapper mapper;
    private final ProcessingAgentCommandMapper commandMapper;
    private final AgentAuditService auditService;
    private final Clock clock;

    public AgentAdministrationService(ProcessingAgentMapper mapper,
                                      ProcessingAgentCommandMapper commandMapper,
                                      AgentAuditService auditService, Clock clock) {
        this.mapper = mapper;
        this.commandMapper = commandMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void setEnabled(String agentId, boolean enabled, int actorUserId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (mapper.setEnabled(agentId, enabled, now) != 1) {
            throw new IllegalArgumentException("处理服务器不存在");
        }
        if (!enabled) {
            commandMapper.cancelActiveForAgent(agentId, "AGENT_DISABLED", now);
        }
        auditService.recordSuccess(enabled ? "AGENT_ENABLE" : "AGENT_DISABLE", actorUserId, agentId, null);
    }

    @Transactional
    public void remove(String agentId, int actorUserId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (mapper.softRemove(agentId, now) != 1) {
            throw new IllegalArgumentException("处理服务器不存在");
        }
        commandMapper.cancelActiveForAgent(agentId, "AGENT_REMOVED", now);
        auditService.recordSuccess("AGENT_REMOVE", actorUserId, agentId, null);
    }
}
