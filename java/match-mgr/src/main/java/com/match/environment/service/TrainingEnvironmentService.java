package com.match.environment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.environment.model.ContainerPortSpec;
import com.match.environment.model.CreateTrainingEnvironmentRequest;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.model.EnvironmentComponentSpec;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.model.TrainingEnvironmentOperationView;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.guard.LicenseGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TrainingEnvironmentService {
    private final ProcessingAgentMapper agentMapper;
    private final ProcessingEnvironmentSlotMapper slotMapper;
    private final ContainerTemplateMapper templateMapper;
    private final TrainingEnvironmentMapper environmentMapper;
    private final EnvironmentPortAllocationMapper portMapper;
    private final EnvironmentOperationMapper operationMapper;
    private final AgentCommandService commandService;
    private final LicenseGuard licenseGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TrainingEnvironmentService(ProcessingAgentMapper agentMapper,
                                      ProcessingEnvironmentSlotMapper slotMapper,
                                      ContainerTemplateMapper templateMapper,
                                      TrainingEnvironmentMapper environmentMapper,
                                      EnvironmentPortAllocationMapper portMapper,
                                      EnvironmentOperationMapper operationMapper,
                                      AgentCommandService commandService,
                                      LicenseGuard licenseGuard,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        this.agentMapper = agentMapper;
        this.slotMapper = slotMapper;
        this.templateMapper = templateMapper;
        this.environmentMapper = environmentMapper;
        this.portMapper = portMapper;
        this.operationMapper = operationMapper;
        this.commandService = commandService;
        this.licenseGuard = licenseGuard;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public TrainingEnvironmentOperationView create(CreateTrainingEnvironmentRequest request, int actorUserId,
                                                    String actorRole) {
        licenseGuard.requireActive();
        validateRequest(request);
        ProcessingAgentRecord agent = agentMapper.selectForManagement(request.getAgentId());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }
        ContainerTemplateRecord annotation = requireTemplate(request.getAnnotationTemplateId(),
                request.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = requireTemplate(request.getEditorTemplateId(),
                request.getEditorTemplateVersion(), "EDITOR");

        ProcessingEnvironmentSlotRecord assignedToUser = slotMapper.selectByAgentAndUserForUpdate(
                request.getAgentId(), request.getUserId());
        if (assignedToUser != null && !request.getSlotNumber().equals(assignedToUser.getSlotNumber())) {
            throw new IllegalArgumentException("该用户已分配其他处理服务器槽位");
        }
        ProcessingEnvironmentSlotRecord slot = assignedToUser == null
                ? slotMapper.selectByAgentAndNumberForUpdate(request.getAgentId(), request.getSlotNumber())
                : assignedToUser;
        if (slot == null) {
            slot = new ProcessingEnvironmentSlotRecord();
            slot.setSlotId(UUID.randomUUID().toString());
            slot.setAgentId(request.getAgentId());
            slot.setSlotNumber(request.getSlotNumber());
            slot.setUserId(request.getUserId());
            slot.setCreatedBy(actorUserId);
            slot.setCreatedAt(now());
            slot.setUpdatedAt(now());
            slotMapper.insert(slot);
        } else if (!request.getUserId().equals(slot.getUserId())) {
            throw new IllegalArgumentException("处理服务器槽位已分配给其他用户");
        }

        TrainingEnvironmentRecord duplicate = environmentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TrainingEnvironmentRecord>()
                        .eq("user_id", request.getUserId()).eq("course_id", request.getCourseId()));
        if (duplicate != null) {
            throw new IllegalArgumentException("该用户课程环境已存在");
        }

        String environmentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        String workspace = "training/" + request.getUserId() + "/" + request.getCourseId();
        String shortId = environmentId.substring(0, 8);
        String annotationName = "xkp-train-" + shortId + "-annotation";
        String editorName = "xkp-train-" + shortId + "-editor";
        List<EnvironmentPortBinding> annotationPorts = ports(annotation, request.getSlotNumber(), "ANNOTATION");
        List<EnvironmentPortBinding> editorPorts = ports(editor, request.getSlotNumber(), "EDITOR");
        reservePorts(slot, annotation, annotationPorts);
        reservePorts(slot, editor, editorPorts);

        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId(environmentId);
        environment.setUserId(request.getUserId());
        environment.setCourseId(request.getCourseId());
        environment.setAgentId(request.getAgentId());
        environment.setSlotId(slot.getSlotId());
        environment.setSlotNumber(slot.getSlotNumber());
        environment.setAnnotationTemplateId(annotation.getTemplateId());
        environment.setAnnotationTemplateVersion(annotation.getTemplateVersion());
        environment.setEditorTemplateId(editor.getTemplateId());
        environment.setEditorTemplateVersion(editor.getTemplateVersion());
        environment.setWorkspaceRelativePath(workspace);
        environment.setDesiredState("STOPPED");
        environment.setActualState("CREATING");
        environment.setAnnotationContainerName(annotationName);
        environment.setAnnotationContainerState("UNKNOWN");
        environment.setEditorContainerName(editorName);
        environment.setEditorContainerState("UNKNOWN");
        environment.setCurrentOperationId(operationId);
        environment.setLockVersion(0L);
        environment.setCreatedBy(actorUserId);
        environment.setCreatedAt(now());
        environment.setUpdatedBy(actorUserId);
        environment.setUpdatedAt(now());
        environmentMapper.insert(environment);

        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId(operationId);
        operation.setEnvironmentId(environmentId);
        operation.setOperationType("CREATE");
        operation.setActorUserId(actorUserId);
        operation.setActorRole(actorRole);
        operation.setState("PENDING");
        operation.setActiveOperationKey(environmentId + ":CREATE");
        operation.setCorrelationId(UUID.randomUUID().toString());
        operation.setRequestedAt(now());
        operation.setUpdatedAt(now());
        operationMapper.insert(operation);

        EnvironmentCommandPayload payload = payload(environment, annotation, editor,
                annotationPorts, editorPorts, operationId);
        AgentCommandView command = commandService.requestEnvironmentCommand(agent, "CREATE_TRAINING_ENVIRONMENT",
                writeJson(payload), actorUserId, actorRole, environmentId + ":CREATE");
        operation.setCommandId(command.getCommandId());
        operationMapper.updateById(operation);

        TrainingEnvironmentOperationView view = new TrainingEnvironmentOperationView();
        view.setEnvironmentId(environmentId);
        view.setOperationId(operationId);
        view.setCommand(command);
        view.setState("PENDING");
        return view;
    }

    private EnvironmentCommandPayload payload(TrainingEnvironmentRecord environment,
                                              ContainerTemplateRecord annotation,
                                              ContainerTemplateRecord editor,
                                              List<EnvironmentPortBinding> annotationPorts,
                                              List<EnvironmentPortBinding> editorPorts,
                                              String operationId) {
        EnvironmentCommandPayload payload = new EnvironmentCommandPayload();
        payload.setEnvironmentId(environment.getEnvironmentId());
        payload.setOperationId(operationId);
        payload.setWorkspaceRelativePath(environment.getWorkspaceRelativePath());
        payload.setComponents(Arrays.asList(component(annotation, environment.getAnnotationContainerName(), annotationPorts),
                component(editor, environment.getEditorContainerName(), editorPorts)));
        return payload;
    }

    private EnvironmentComponentSpec component(ContainerTemplateRecord template, String name,
                                               List<EnvironmentPortBinding> ports) {
        EnvironmentComponentSpec component = new EnvironmentComponentSpec();
        component.setComponentType(template.getComponentType());
        component.setContainerName(name);
        component.setConfigFingerprint(template.getConfigFingerprint());
        component.setImageReference(template.getImageReference());
        component.setRuntimeName(template.getRuntimeName());
        component.setRestartPolicy(template.getRestartPolicy());
        component.setMountTarget(template.getMountTarget());
        component.setPorts(ports);
        component.setCpuLimitMillis(template.getCpuLimitMillis());
        component.setMemoryLimitBytes(template.getMemoryLimitBytes());
        component.setGpuEnabled(template.getGpuEnabled());
        component.setGpuComputePercent(template.getGpuComputePercent());
        component.setGpuMemoryLimitBytes(template.getGpuMemoryLimitBytes());
        if (template.getCommandJson() != null) {
            try { component.setCommand(objectMapper.readValue(template.getCommandJson(), List.class)); }
            catch (Exception exception) { throw new IllegalStateException("模板命令无效", exception); }
        }
        component.setWorkingDirectory(template.getWorkingDirectory());
        return component;
    }

    private List<EnvironmentPortBinding> ports(ContainerTemplateRecord template, int slot, String componentType) {
        try {
            List<ContainerPortSpec> source = objectMapper.readValue(template.getPortsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ContainerPortSpec.class));
            List<EnvironmentPortBinding> result = new ArrayList<>();
            int index = 0;
            for (ContainerPortSpec port : source) {
                EnvironmentPortBinding binding = new EnvironmentPortBinding();
                binding.setContainerPort(port.getContainerPort());
                binding.setProtocol(port.getProtocol());
                binding.setHostPort(defaultHostPort(slot, componentType, index++));
                result.add(binding);
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("模板端口配置无效", exception);
        }
    }

    private int defaultHostPort(int slot, String componentType, int index) {
        if ("ANNOTATION".equals(componentType)) return 8080 + slot;
        if (index == 0) return 9090 + slot;
        if (index == 1) return 8880 + slot;
        return 5000 + slot;
    }

    private void reservePorts(ProcessingEnvironmentSlotRecord slot, ContainerTemplateRecord template,
                              List<EnvironmentPortBinding> ports) {
        for (EnvironmentPortBinding port : ports) {
            EnvironmentPortAllocationRecord existing = portMapper.selectAgentPortForUpdate(
                    slot.getAgentId(), port.getHostPort(), port.getProtocol());
            if (existing != null && Objects.equals(existing.getSlotId(), slot.getSlotId())
                    && Objects.equals(existing.getComponentType(), template.getComponentType())
                    && Objects.equals(existing.getContainerPort(), port.getContainerPort())) {
                continue;
            }
            if (existing != null) {
                throw new IllegalArgumentException("主机端口已被占用: " + port.getHostPort());
            }
            EnvironmentPortAllocationRecord allocation = new EnvironmentPortAllocationRecord();
            allocation.setAllocationId(UUID.randomUUID().toString());
            allocation.setSlotId(slot.getSlotId());
            allocation.setAgentId(slot.getAgentId());
            allocation.setComponentType(template.getComponentType());
            allocation.setContainerPort(port.getContainerPort());
            allocation.setHostPort(port.getHostPort());
            allocation.setProtocol(port.getProtocol());
            allocation.setCreatedAt(now());
            portMapper.insert(allocation);
        }
    }

    private ContainerTemplateRecord requireTemplate(String id, Integer version, String type) {
        if (id == null || version == null) throw new IllegalArgumentException("模板版本不能为空");
        ContainerTemplateRecord template = templateMapper.selectVersion(id, version);
        if (template == null || !Boolean.TRUE.equals(template.getEnabled()) || !type.equals(template.getComponentType())) {
            throw new IllegalArgumentException("模板版本不可用");
        }
        return template;
    }

    private void validateRequest(CreateTrainingEnvironmentRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId() < 1
                || request.getCourseId() == null || request.getCourseId() < 1
                || request.getAgentId() == null || request.getSlotNumber() == null
                || request.getSlotNumber() < 1 || request.getSlotNumber() > 4) {
            throw new IllegalArgumentException("实训环境分配参数不正确");
        }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("环境命令无法序列化", exception); }
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
