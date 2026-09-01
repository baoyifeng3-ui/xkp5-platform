package com.match.agent.service;

import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.stereotype.Service;

@Service
public class AgentUpgradeService {
    private final ProcessingAgentMapper agents; private final AgentCommandService commands; private final AgentPackageService packages;
    public AgentUpgradeService(ProcessingAgentMapper agents, AgentCommandService commands, AgentPackageService packages) {
        this.agents = agents; this.commands = commands; this.packages = packages;
    }
    public AgentCommandView upgrade(String agentId, int userId) {
        ProcessingAgentRecord agent = agents.selectForManagement(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) throw new IllegalArgumentException("处理服务器不可用");
        return commands.requestUpgrade(agent, AgentPackageService.AGENT_VERSION, packages.upgradeSha256(), userId, "SUPER_ADMIN");
    }
}
