package com.match.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ModelWorkspaceService {
    private final TrainingEnvironmentMapper environments;
    private final ProcessingAgentMapper agents;
    private final ProcessingAgentCommandMapper commandRecords;
    private final AgentCommandService commands;
    private final ObjectMapper json;

    public ModelWorkspaceService(TrainingEnvironmentMapper environments, ProcessingAgentMapper agents,
                                 ProcessingAgentCommandMapper commandRecords, AgentCommandService commands,
                                 ObjectMapper json) {
        this.environments = environments;
        this.agents = agents;
        this.commandRecords = commandRecords;
        this.commands = commands;
        this.json = json;
    }

    public AgentCommandView list(int userId, boolean admin, String environmentId) {
        return request(userId, admin, environmentId, "LIST", null);
    }

    public AgentCommandView deploy(int userId, boolean admin, String environmentId, ModelWorkspaceRequest request) {
        if (request == null || blank(request.getModelPath()) || blank(request.getConfigPath())) {
            throw new IllegalArgumentException("请选择模型文件和配置文件");
        }
        return request(userId, admin, environmentId, "DEPLOY", request);
    }

    public Map<String, Object> status(int userId, boolean admin, String commandId) {
        ProcessingAgentCommandRecord record = commandRecords.selectById(commandId);
        if (record == null || !"MODEL_WORKSPACE".equals(record.getCommandType())
                || (!admin && !Integer.valueOf(userId).equals(record.getRequesterUserId()))) {
            throw new IllegalArgumentException("模型部署任务不存在");
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("commandId", record.getCommandId());
        view.put("state", record.getState());
        view.put("resultCode", record.getResultCode());
        view.put("resultMessage", record.getResultMessage());
        if (!blank(record.getResultJson())) {
            try {
                view.put("details", json.readValue(record.getResultJson(), new TypeReference<Map<String, Object>>() { }));
            } catch (Exception ignored) {
                view.put("details", new LinkedHashMap<>());
            }
        }
        return view;
    }

    private AgentCommandView request(int userId, boolean admin, String environmentId, String action,
                                     ModelWorkspaceRequest request) {
        TrainingEnvironmentRecord environment = environments.selectById(environmentId);
        if (environment == null || (!admin && !Integer.valueOf(userId).equals(environment.getUserId()))) {
            throw new IllegalArgumentException("无权操作该实训环境");
        }
        if (blank(environment.getEditorContainerName())) {
            throw new IllegalArgumentException("当前实训环境没有代码编辑容器");
        }
        ProcessingAgentRecord agent = agents.selectForManagement(environment.getAgentId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("containerName", environment.getEditorContainerName());
        payload.put("modelPath", request == null ? "" : request.getModelPath());
        payload.put("configPath", request == null ? "" : request.getConfigPath());
        payload.put("overwrite", request != null && Boolean.TRUE.equals(request.getOverwrite()));
        try {
            return commands.requestEnvironmentCommand(agent, "MODEL_WORKSPACE", json.writeValueAsString(payload),
                    userId, admin ? "ADMIN" : "USER", environmentId + ":MODEL:" + UUID.randomUUID());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("模型部署任务提交失败", exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
