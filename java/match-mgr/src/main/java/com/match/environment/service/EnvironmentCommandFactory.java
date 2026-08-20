package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.model.EnvironmentComponentSpec;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class EnvironmentCommandFactory {
    private final ContainerTemplateMapper templateMapper;
    private final EnvironmentPortAllocationMapper portMapper;
    private final ObjectMapper objectMapper;

    public EnvironmentCommandFactory(ContainerTemplateMapper templateMapper,
                                     EnvironmentPortAllocationMapper portMapper,
                                     ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.portMapper = portMapper;
        this.objectMapper = objectMapper;
    }

    public String createPayloadJson(TrainingEnvironmentRecord environment, String operationId) {
        ContainerTemplateRecord annotation = requireTemplate(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = requireTemplate(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), "EDITOR");
        List<EnvironmentPortAllocationRecord> allocations = portMapper.selectBySlot(environment.getSlotId());
        EnvironmentCommandPayload payload = new EnvironmentCommandPayload();
        payload.setEnvironmentId(environment.getEnvironmentId());
        payload.setOperationId(operationId);
        payload.setWorkspaceRelativePath(environment.getWorkspaceRelativePath());
        payload.setComponents(Arrays.asList(
                component(annotation, environment.getAnnotationContainerName(), allocations),
                component(editor, environment.getEditorContainerName(), allocations)));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("环境命令无法序列化", exception);
        }
    }

    private EnvironmentComponentSpec component(ContainerTemplateRecord template, String containerName,
                                               List<EnvironmentPortAllocationRecord> allocations) {
        EnvironmentComponentSpec component = new EnvironmentComponentSpec();
        component.setComponentType(template.getComponentType());
        component.setContainerName(containerName);
        component.setConfigFingerprint(template.getConfigFingerprint());
        component.setImageReference(template.getImageReference());
        component.setRuntimeName(template.getRuntimeName());
        component.setRestartPolicy(template.getRestartPolicy());
        component.setMountTarget(template.getMountTarget());
        component.setPorts(ports(template.getComponentType(), allocations));
        component.setCpuLimitMillis(template.getCpuLimitMillis());
        component.setMemoryLimitBytes(template.getMemoryLimitBytes());
        component.setGpuEnabled(template.getGpuEnabled());
        component.setGpuComputePercent(template.getGpuComputePercent());
        component.setGpuMemoryLimitBytes(template.getGpuMemoryLimitBytes());
        component.setWorkingDirectory(template.getWorkingDirectory());
        if (template.getCommandJson() != null) {
            try {
                component.setCommand(objectMapper.readValue(template.getCommandJson(), List.class));
            } catch (Exception exception) {
                throw new IllegalStateException("模板命令无效", exception);
            }
        }
        return component;
    }

    private List<EnvironmentPortBinding> ports(String componentType,
                                               List<EnvironmentPortAllocationRecord> allocations) {
        List<EnvironmentPortBinding> result = new ArrayList<>();
        if (allocations != null) {
            for (EnvironmentPortAllocationRecord allocation : allocations) {
                if (!componentType.equals(allocation.getComponentType())) {
                    continue;
                }
                EnvironmentPortBinding port = new EnvironmentPortBinding();
                port.setContainerPort(allocation.getContainerPort());
                port.setHostPort(allocation.getHostPort());
                port.setProtocol(allocation.getProtocol());
                result.add(port);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("环境端口分配缺失: " + componentType);
        }
        return result;
    }

    private ContainerTemplateRecord requireTemplate(String id, Integer version, String componentType) {
        ContainerTemplateRecord template = templateMapper.selectVersion(id, version);
        if (template == null || !componentType.equals(template.getComponentType())) {
            throw new IllegalStateException("环境固定模板不存在: " + componentType);
        }
        return template;
    }
}
