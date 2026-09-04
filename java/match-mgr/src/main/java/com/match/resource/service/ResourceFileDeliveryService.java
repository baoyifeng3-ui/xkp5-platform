package com.match.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.resource.persistence.PlatformFileMapper;
import com.match.resource.persistence.PlatformFileRecord;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ResourceFileDeliveryService {
    private final PlatformFileMapper files;
    private final TrainingEnvironmentMapper environments;
    private final ProcessingAgentMapper agents;
    private final AgentCommandService commands;
    private final ObjectMapper objectMapper;

    public ResourceFileDeliveryService(PlatformFileMapper files,
                                       TrainingEnvironmentMapper environments,
                                       ProcessingAgentMapper agents, AgentCommandService commands,
                                       ObjectMapper objectMapper) {
        this.files = files; this.environments = environments; this.agents = agents;
        this.commands = commands; this.objectMapper = objectMapper;
    }

    public AgentCommandView deliverPublicFile(String fileId, String environmentId, Integer userId) {
        return deliverFile(fileId, environmentId, userId, "PUBLIC");
    }
    public AgentCommandView deliverFile(String fileId, String environmentId, Integer userId, String space) {
        PlatformFileRecord file = files.selectScoped(fileId, space, space.equals("HOMEWORK") ? userId : null);
        if (file == null) throw new ResourceOperationException(404, "FILE_NOT_FOUND", "资源不存在");
        TrainingEnvironmentRecord environment = environments.selectForUpdate(environmentId);
        if (environment == null || !userId.equals(environment.getUserId())) {
            throw new ResourceOperationException(403, "ENVIRONMENT_NOT_OWNED", "实训环境不存在或不属于当前用户");
        }
        ProcessingAgentRecord agent = agents.selectForManagement(environment.getAgentId());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
            throw new ResourceOperationException(503, "AGENT_UNAVAILABLE", "处理服务器不可用");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("downloadPath", "/agent/v1/files/" + file.getStorageKey());
            payload.put("targetRelativePath", environment.getWorkspaceRelativePath() + "/resource-downloads/" + file.getFileName());
            payload.put("sha256", file.getSha256());
            return commands.requestEnvironmentCommand(agent, "TRANSFER_FILE",
                    objectMapper.writeValueAsString(payload), userId, "USER",
                    environmentId + ":RESOURCE:" + fileId);
        } catch (ResourceOperationException error) {
            throw error;
        } catch (Exception error) {
            throw new ResourceOperationException(503, "DELIVERY_FAILED", "资源下发失败");
        }
    }
}
