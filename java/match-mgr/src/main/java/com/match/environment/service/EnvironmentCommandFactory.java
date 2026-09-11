package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.environment.model.EnvironmentCommandPayload;
import com.match.environment.model.EnvironmentComponentSpec;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.model.ContainerPortSpec;
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
                environment.getEditorTemplateVersion(), environment.getEditorContainerName(), null, false);
    }

    public String createPayloadJson(CompetitionEnvironmentRecord environment, String operationId) {
        return createPayloadJson(environment.getEnvironmentId(), operationId,
                environment.getWorkspaceRelativePath(), environment.getSlotId(),
                environment.getAnnotationTemplateId(), environment.getAnnotationTemplateVersion(),
                environment.getAnnotationContainerName(), environment.getAnnotationConfigFingerprint(),
                environment.getEditorTemplateId(), environment.getEditorTemplateVersion(),
                environment.getEditorContainerName(), environment.getEditorConfigFingerprint(), true);
    }

    public String createControlPayloadJson(TrainingEnvironmentRecord environment, String operationId) {
        ContainerTemplateRecord annotation = optionalTemplate(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = optionalTemplate(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), "EDITOR");
        return controlPayload(environment.getEnvironmentId(), operationId,
                identity(annotation, environment.getAnnotationContainerName(), null),
                identity(editor, environment.getEditorContainerName(), null));
    }

    public String createControlPayloadJson(CompetitionEnvironmentRecord environment, String operationId) {
        ContainerTemplateRecord annotation = optionalTemplate(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = optionalTemplate(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), "EDITOR");
        return controlPayload(environment.getEnvironmentId(), operationId,
                identity(annotation, environment.getAnnotationContainerName(),
                        environment.getAnnotationConfigFingerprint()),
                identity(editor, environment.getEditorContainerName(),
                        environment.getEditorConfigFingerprint()));
    }

    private String controlPayload(String environmentId, String operationId,
                                  EnvironmentComponentSpec annotation,
                                  EnvironmentComponentSpec editor) {
        EnvironmentCommandPayload payload = new EnvironmentCommandPayload();
        payload.setEnvironmentId(environmentId);
        payload.setOperationId(operationId);
        List<EnvironmentComponentSpec> components = new ArrayList<>();
        if (annotation != null) components.add(annotation);
        if (editor != null) components.add(editor);
        payload.setComponents(components);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("环境命令无法序列化", exception);
        }
    }

    private EnvironmentComponentSpec identity(ContainerTemplateRecord template, String containerName,
                                               String pinnedFingerprint) {
        if (template == null) return null;
        EnvironmentComponentSpec component = new EnvironmentComponentSpec();
        component.setComponentType(template.getComponentType());
        component.setContainerName(containerName);
        component.setConfigFingerprint(pinnedFingerprint == null
                ? template.getConfigFingerprint() : pinnedFingerprint);
        component.setMpsEnabled(template.getMpsEnabled());
        component.setGpuComputePercent(template.getGpuComputePercent());
        return component;
    }

    private String createPayloadJson(String environmentId, String operationId, String workspaceRelativePath,
                                     String slotId, String annotationTemplateId,
                                     Integer annotationTemplateVersion, String annotationContainerName,
                                     String annotationConfigFingerprint,
                                     String editorTemplateId, Integer editorTemplateVersion,
                                     String editorContainerName, String editorConfigFingerprint,
                                     boolean pinnedCompetitionPair) {
        ContainerTemplateRecord annotation = optionalTemplate(annotationTemplateId,
                annotationTemplateVersion, "ANNOTATION");
        ContainerTemplateRecord editor = optionalTemplate(editorTemplateId,
                editorTemplateVersion, "EDITOR");
        requirePinnedFingerprint(annotation, annotationConfigFingerprint, "ANNOTATION",
                pinnedCompetitionPair);
        requirePinnedFingerprint(editor, editorConfigFingerprint, "EDITOR",
                pinnedCompetitionPair);
        List<EnvironmentPortAllocationRecord> allocations = portMapper.selectByEnvironment(environmentId);
        if(allocations==null||allocations.isEmpty())allocations=portMapper.selectBySlot(slotId);
        EnvironmentCommandPayload payload = new EnvironmentCommandPayload();
        payload.setEnvironmentId(environmentId);
        payload.setOperationId(operationId);
        payload.setWorkspaceRelativePath(workspaceRelativePath);
        List<EnvironmentComponentSpec> components=new ArrayList<>();
        if(annotation!=null)components.add(component(annotation,annotationContainerName,allocations,pinnedCompetitionPair));
        if(editor!=null)components.add(component(editor,editorContainerName,allocations,pinnedCompetitionPair));
        payload.setComponents(components);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("环境命令无法序列化", exception);
        }
    }

    private ContainerTemplateRecord optionalTemplate(String id,Integer version,String type){if(id==null&&version==null)return null;return requireTemplate(id,version,type);}

    private EnvironmentComponentSpec component(ContainerTemplateRecord template, String containerName,
                                               List<EnvironmentPortAllocationRecord> allocations,
                                               boolean exactTemplatePorts) {
        EnvironmentComponentSpec component = new EnvironmentComponentSpec();
        component.setComponentType(template.getComponentType());
        component.setContainerName(containerName);
        component.setConfigFingerprint(template.getConfigFingerprint());
        component.setImageReference(template.getImageId() == null ? template.getImageReference() : template.getImageId());
        component.setRuntimeName(template.getRuntimeName());
        component.setRestartPolicy(template.getRestartPolicy());
        component.setMountTarget(template.getMountTarget());
        component.setPorts(exactTemplatePorts
                ? exactTemplatePorts(template, allocations)
                : ports(template.getComponentType(), allocations));
        component.setCpuLimitMillis(template.getCpuLimitMillis());
        component.setMemoryLimitBytes(template.getMemoryLimitBytes());
        component.setGpuEnabled(template.getGpuEnabled());
        component.setGpuComputePercent(template.getGpuComputePercent());
        component.setGpuMemoryLimitBytes(template.getGpuMemoryLimitBytes());
        component.setMpsEnabled(template.getMpsEnabled());
        component.setWorkingDirectory(template.getWorkingDirectory());
        if (template.getCommandJson() != null) {
            try {
                component.setCommand(objectMapper.readValue(template.getCommandJson(), List.class));
            } catch (Exception exception) {
                throw new IllegalStateException("模板命令无效", exception);
            }
        } else if ("EDITOR".equals(template.getComponentType())) {
            component.setCommand(DefaultEditorCommand.value());
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

    private List<EnvironmentPortBinding> exactTemplatePorts(
            ContainerTemplateRecord template, List<EnvironmentPortAllocationRecord> allocations) {
        List<ContainerPortSpec> declared;
        try {
            declared = objectMapper.readValue(template.getPortsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, ContainerPortSpec.class));
        } catch (Exception exception) {
            throw new IllegalStateException("environment pinned template ports are invalid: "
                    + template.getComponentType(), exception);
        }
        if (declared == null || declared.isEmpty()) {
            throw new IllegalStateException("environment pinned template ports are invalid: "
                    + template.getComponentType());
        }
        List<EnvironmentPortBinding> result = new ArrayList<>();
        for (ContainerPortSpec port : declared) {
            EnvironmentPortAllocationRecord match = null;
            if (allocations != null) {
                for (EnvironmentPortAllocationRecord allocation : allocations) {
                    if (template.getComponentType().equals(allocation.getComponentType())
                            && port.getContainerPort().equals(allocation.getContainerPort())
                            && port.getProtocol().equals(allocation.getProtocol())) {
                        if (match != null) {
                            throw new IllegalStateException("environment port allocation is ambiguous: "
                                    + template.getComponentType());
                        }
                        match = allocation;
                    }
                }
            }
            if (match == null) {
                throw new IllegalStateException("environment port allocation is incomplete: "
                        + template.getComponentType());
            }
            if (match.getHostPort() == null || match.getHostPort() < 1
                    || match.getHostPort() > 65535) {
                throw new IllegalStateException("environment host port allocation is invalid: "
                        + template.getComponentType());
            }
            EnvironmentPortBinding binding = new EnvironmentPortBinding();
            binding.setContainerPort(match.getContainerPort());
            binding.setHostPort(match.getHostPort());
            binding.setProtocol(match.getProtocol());
            result.add(binding);
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
                                          String componentType, boolean required) {
        if ((required && pinned == null)
                || (pinned != null && !pinned.equals(template.getConfigFingerprint()))) {
            throw new IllegalStateException("环境固定模板指纹不匹配: " + componentType);
        }
    }
}
