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
        if (!isNewer(agent.getAgentVersion(), AgentPackageService.AGENT_VERSION))
            throw new IllegalArgumentException("当前 Agent 无可用升级，不允许重复安装或降级");
        return commands.requestUpgrade(agent, AgentPackageService.AGENT_VERSION, packages.upgradeSha256(), userId, "SUPER_ADMIN");
    }

    public static boolean isNewer(String current, String target) {
        if (current == null || target == null || !current.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
                || !target.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")) return false;
        String[] a = current.split("\\."), b = target.split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(b[i]), Integer.parseInt(a[i]));
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }
}
