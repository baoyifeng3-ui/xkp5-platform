package com.match.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.match.agent.model.*;
import com.match.agent.persistence.*;
import com.match.agent.service.AgentCommandService;
import com.match.environment.persistence.*;
import com.match.environment.service.EnvironmentOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class DockerInventoryService {
    private static final String TYPE = "DOCKER_INVENTORY_ACTION";
    private final ProcessingAgentMapper agents;
    private final ProcessingAgentCommandMapper records;
    private final AgentCommandService commands;
    private final ContainerTemplateMapper templates;
    private final TrainingEnvironmentMapper environments;
    private final EnvironmentOperationService operations;
    private final ObjectMapper json;
    private final Clock clock;

    public DockerInventoryService(ProcessingAgentMapper agents, ProcessingAgentCommandMapper records,
            AgentCommandService commands, ContainerTemplateMapper templates, TrainingEnvironmentMapper environments,
            EnvironmentOperationService operations, ObjectMapper json, Clock clock) {
        this.agents=agents; this.records=records; this.commands=commands; this.templates=templates;
        this.environments=environments; this.operations=operations; this.json=json; this.clock=clock;
    }

    public List<AgentDockerImageView> images(String agentId) {
        List<AgentDockerImageView> images = metrics(requireAgent(agentId)).getImages();
        if (images == null) throw new IllegalArgumentException("服务器尚未上报镜像清单，请升级 Agent 后刷新");
        return images;
    }

    public void requireCurrentImage(String reference) {
        for (ProcessingAgentRecord agent : agents.selectVisibleAgents()) {
            if (!Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null || agent.getLastSeenAt() == null
                    || agent.getLastSeenAt().isBefore(now().minusSeconds(90))) continue;
            try {
                List<AgentDockerImageView> images = metrics(agent).getImages();
                if (images != null && images.stream().anyMatch(i -> i != null &&
                        (reference.equals(reference(i)) || i.getRepoTags() != null && i.getRepoTags().contains(reference)))) return;
            } catch (IllegalArgumentException unavailable) { /* Other online servers may still provide the image. */ }
        }
        throw new IllegalArgumentException("在线服务器上不存在该镜像，请完成下发并刷新镜像清单后再创建模板");
    }

    @Transactional
    public AgentCommandView inspect(String agentId, String target, int actor) {
        return request(requireAgent(agentId), "INSPECT_IMAGE", target, actor);
    }

    @Transactional
    public AgentCommandView deleteImage(String agentId, String target, String imageId, int actor) {
        ProcessingAgentRecord agent = lockAgent(agentId);
        AgentDockerImageView image = images(agentId).stream()
                .filter(i -> Objects.equals(imageId, i.getId()) && Objects.equals(target, reference(i)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("镜像清单已变化，请刷新后重试"));
        if (templates.countImageReferences(target, image.getId()) > 0)
            throw new IllegalArgumentException("镜像已创建容器模板，请先删除引用该镜像的模板");
        // Delete the immutable ID checked above, never a tag which may have moved.
        return request(agent, "DELETE_IMAGE", image.getId(), actor);
    }

    @Transactional
    public AgentCommandView deleteContainer(String agentId, String target, String containerId, int actor) {
        ProcessingAgentRecord agent = lockAgent(agentId);
        List<AgentDockerContainerView> containers = metrics(agent).getContainers();
        if (containers == null) containers = Collections.emptyList();
        AgentDockerContainerView container = containers.stream()
                .filter(c -> Objects.equals(containerId, c.getId()) && Objects.equals(target, c.getName()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("容器清单已变化，请刷新后重试"));
        for (TrainingEnvironmentRecord env : environments.selectByAgentForUpdate(agentId)) {
            if (target.equals(env.getAnnotationContainerName()) || target.equals(env.getEditorContainerName()))
                return operations.delete(env.getEnvironmentId(), actor, "SUPER_ADMIN").getCommand();
        }
        return request(agent, "DELETE_CONTAINER", container.getId(), actor);
    }

    public Map<String,Object> status(String agentId, String commandId, int actor) {
        ProcessingAgentCommandRecord command = ownedCommand(agentId, commandId, actor);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("commandId", commandId); result.put("state", command.getState());
        result.put("resultCode", command.getResultCode()); result.put("resultMessage", command.getResultMessage());
        result.put("resultJson", command.getResultJson()); return result;
    }

    @Transactional
    public AgentDockerImageView inspectedImage(String agentId, String commandId, String reference, int actor) {
        lockAgent(agentId);
        if (records.selectActive(agentId, TYPE) != null)
            throw new IllegalArgumentException("服务器镜像操作尚未完成，请完成后重新检查镜像");
        ProcessingAgentCommandRecord command = ownedCommand(agentId, commandId, actor);
        JsonNode payload = parse(command.getPayloadJson());
        if (!TYPE.equals(command.getCommandType()) || !"INSPECT_IMAGE".equals(payload.path("action").asText())
                || !reference.equals(payload.path("target").asText()) || !"SUCCEEDED".equals(command.getState())
                || command.getCompletedAt() == null || command.getCompletedAt().isBefore(now().minusMinutes(2)))
            throw new IllegalArgumentException("请重新检查服务器上的实际镜像后创建模板");
        if (records.countImageDeletesSince(agentId, command.getRequestedAt()) > 0)
            throw new IllegalArgumentException("检查后执行过镜像删除，请重新检查镜像");
        try {
            AgentDockerImageView image = json.treeToValue(parse(command.getResultJson()), AgentDockerImageView.class);
            if (image.getId() == null || !image.getId().matches("sha256:[0-9a-f]{64}") || !reference.equals(reference(image)))
                throw new IllegalArgumentException("服务器返回的镜像身份无效");
            return image;
        } catch (java.io.IOException error) { throw new IllegalArgumentException("服务器镜像信息无效", error); }
    }

    private AgentCommandView request(ProcessingAgentRecord agent, String action, String target, int actor) {
        if (target == null || target.length() > 255 || !target.matches("[A-Za-z0-9][A-Za-z0-9._/:@-]*"))
            throw new IllegalArgumentException("镜像或容器名称无效");
        ObjectNode payload = json.createObjectNode(); payload.put("action",action); payload.put("target",target);
        return commands.requestEnvironmentCommand(agent, TYPE, payload.toString(), actor, "SUPER_ADMIN", UUID.randomUUID().toString());
    }

    private ProcessingAgentCommandRecord ownedCommand(String agentId, String id, int actor) {
        ProcessingAgentCommandRecord command = id == null ? null : records.selectById(id);
        if (command == null || !Objects.equals(agentId, command.getAgentId())
                || !Objects.equals(actor, command.getRequesterUserId())
                || !(TYPE.equals(command.getCommandType()) || "DELETE_TRAINING_ENVIRONMENT".equals(command.getCommandType())))
            throw new IllegalArgumentException("操作任务不存在");
        return command;
    }

    private ProcessingAgentRecord lockAgent(String id) {
        if (records.selectEnabledAgentForUpdate(id) == null) throw new IllegalArgumentException("服务器不可用");
        return requireAgent(id);
    }

    private ProcessingAgentRecord requireAgent(String id) {
        ProcessingAgentRecord agent = agents.selectForManagement(id);
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt()!=null
                || agent.getLastSeenAt()==null || agent.getLastSeenAt().isBefore(now().minusSeconds(90)))
            throw new IllegalArgumentException("服务器不在线，请恢复连接后重试");
        return agent;
    }

    private AgentMetricSnapshot metrics(ProcessingAgentRecord agent) {
        if (agent.getLatestMetrics() == null) throw new IllegalArgumentException("服务器尚未上报 Docker 清单");
        try {
            AgentMetricSnapshot metrics = json.readValue(agent.getLatestMetrics(), AgentMetricSnapshot.class);
            if (!Boolean.TRUE.equals(metrics.getDockerAvailable()))
                throw new IllegalArgumentException("服务器尚未上报 Docker 清单，请升级 Agent 后刷新");
            return metrics;
        } catch (java.io.IOException error) { throw new IllegalArgumentException("服务器 Docker 清单不可用",error); }
    }

    private JsonNode parse(String value) {
        try { return value == null ? json.createObjectNode() : json.readTree(value); }
        catch (java.io.IOException error) { throw new IllegalArgumentException("操作结果格式无效",error); }
    }
    private String reference(AgentDockerImageView image) {
        return image.getRepository()==null || image.getRepository().isEmpty() || "<none>".equals(image.getRepository())
                || image.getTag()==null || image.getTag().isEmpty() || "<none>".equals(image.getTag())
                ? image.getId() : image.getRepository()+":"+image.getTag();
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
}
