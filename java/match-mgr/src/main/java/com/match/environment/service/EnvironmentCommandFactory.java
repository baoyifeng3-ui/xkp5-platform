package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.model.EnvironmentComponentSpec;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
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
        return createPayloadJson(environment.getEnvironmentId(), operationId,
                environment.getWorkspaceRelativePath(), environment.getSlotId(),
                environment.getAnnotationTemplateId(), environment.getAnnotationTemplateVersion(),
                environment.getAnnotationContainerName(), null, environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), environment.getEditorContainerName(), null);
    }

    public String createPayloadJson(CompetitionEnvironmentRecord environment, String operationId) {
        return createPayloadJson(environment.getEnvironmentId(), operationId,
                environment.getWorkspaceRelativePath(), environment.getSlotId(),
                environment.getAnnotationTemplateId(), environment.getAnnotationTemplateVersion(),
                environment.getAnnotationContainerName(), environment.getAnnotationConfigFingerprint(),
                environment.getEditorTemplateId(), environment.getEditorTemplateVersion(),
                environment.getEditorContainerName(), environment.getEditorConfigFingerprint());
    }

    private String createPayloadJson(String environmentId, String operationId, String workspaceRelativePath,
                                     String slotId, String annotationTemplateId,
                                     Integer annotationTemplateVersion, String annotationContainerName,
                                     String annotationConfigFingerprint,
                                     String editorTemplateId, Integer editorTemplateVersion,
                                     String editorContainerName, String editorConfigFingerprint) {
        ContainerTemplateRecord annotation = requireTemplate(annotationTemplateId,
                annotationTemplateVersion, "ANNOTATION");
        ContainerTemplateRecord editor = requireTemplate(editorTemplateId,
                editorTemplateVersion, "EDITOR");
        requirePinnedFingerprint(annotation, annotationConfigFingerprint, "ANNOTATION");
        requirePinnedFingerprint(editor, editorConfigFingerprint, "EDITOR");
        List<EnvironmentPortAllocationRecord> allocations = portMapper.selectBySlot(slotId);
        EnvironmentCommandPayload payload = new EnvironmentCommandPayload();
        payload.setEnvironmentId(environmentId);
        payload.setOperationId(operationId);
        payload.setWorkspaceRelativePath(workspaceRelativePath);
        payload.setComponents(Arrays.asList(
                component(annotation, annotationContainerName, allocations),
                component(editor, editorContainerName, allocations)));
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

    private void requirePinnedFingerprint(ContainerTemplateRecord template, String pinned,
                                          String componentType) {
        if (pinned != null && !pinned.equals(template.getConfigFingerprint())) {
            throw new IllegalStateException("环境固定模板指纹不匹配: " + componentType);
        }
    }
}
