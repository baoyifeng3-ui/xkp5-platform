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
import com.match.registry.persistence.ImageDeploymentMapper;
import com.match.registry.persistence.ImageDeploymentRecord;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service("trainingEnvironmentLifecycleService")
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
    private ImageDeploymentMapper imageDeploymentMapper;
    private EnvironmentPortPoolService portPoolService;

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

    @Autowired
    public void setImageDeploymentMapper(ImageDeploymentMapper imageDeploymentMapper) {
        this.imageDeploymentMapper = imageDeploymentMapper;
    }
    @Autowired public void setPortPoolService(EnvironmentPortPoolService value){this.portPoolService=value;}

    @Transactional
    public TrainingEnvironmentOperationView create(CreateTrainingEnvironmentRequest request, int actorUserId,
                                                    String actorRole) {
        licenseGuard.requireActive();
        validateRequest(request);
        ProcessingAgentRecord agent = agentMapper.selectForManagement(request.getAgentId());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }
        ContainerTemplateRecord annotation = optionalTemplate(request.getAnnotationTemplateId(),
                request.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = optionalTemplate(request.getEditorTemplateId(),
                request.getEditorTemplateVersion(), "EDITOR");
        if (annotation == null && editor == null) throw new IllegalArgumentException("至少选择一个容器模板");
        verifyServerImage(request.getAgentId(), annotation);
        verifyServerImage(request.getAgentId(), editor);

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
        boolean competition = "COMPETITION".equalsIgnoreCase(request.getEnvironmentType());
        String workspace = (competition ? "competition/" : "training/") + request.getUserId()
                + "/" + (request.getCourseId() == null ? "unbound" : request.getCourseId());
        String shortId = environmentId.substring(0, 8);
        String prefix = competition ? "xkp-comp-" : "xkp-train-";
        String annotationName = annotation == null ? null : prefix + shortId + "-annotation";
        String editorName = editor == null ? null : prefix + shortId + "-editor";
        List<EnvironmentPortBinding> annotationPorts = annotation == null ? new ArrayList<>() : portPoolService==null?ports(annotation,request.getSlotNumber(),"ANNOTATION",request):poolPorts(environmentId,slot,annotation,competition?"COMPETITION":"COURSE");
        List<EnvironmentPortBinding> editorPorts = editor == null ? new ArrayList<>() : portPoolService==null?ports(editor,request.getSlotNumber(),"EDITOR",request):poolPorts(environmentId,slot,editor,competition?"COMPETITION":"COURSE");
        if(portPoolService==null){if(annotation!=null)reservePorts(slot,annotation,annotationPorts);if(editor!=null)reservePorts(slot,editor,editorPorts);}

        TrainingEnvironmentRecord environment = new TrainingEnvironmentRecord();
        environment.setEnvironmentId(environmentId);
        environment.setEnvironmentName(request.getEnvironmentName());
        environment.setRemark(request.getRemark());
        environment.setEnvironmentType(competition ? "COMPETITION" : "COURSE");
        environment.setUserId(request.getUserId());
        environment.setCourseId(request.getCourseId());
        environment.setAgentId(request.getAgentId());
        environment.setSlotId(slot.getSlotId());
        environment.setSlotNumber(slot.getSlotNumber());
        environment.setAnnotationTemplateId(annotation == null ? null : annotation.getTemplateId());
        environment.setAnnotationTemplateVersion(annotation == null ? null : annotation.getTemplateVersion());
        environment.setEditorTemplateId(editor == null ? null : editor.getTemplateId());
        environment.setEditorTemplateVersion(editor == null ? null : editor.getTemplateVersion());
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
        List<EnvironmentComponentSpec> components = new ArrayList<>();
        if (annotation != null) components.add(component(annotation, environment.getAnnotationContainerName(), annotationPorts));
        if (editor != null) components.add(component(editor, environment.getEditorContainerName(), editorPorts));
        payload.setComponents(components);
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
        component.setMpsEnabled(template.getMpsEnabled());
        if (template.getCommandJson() != null) {
            try { component.setCommand(objectMapper.readValue(template.getCommandJson(), List.class)); }
            catch (Exception exception) { throw new IllegalStateException("模板命令无效", exception); }
        } else if ("EDITOR".equals(template.getComponentType())) {
            component.setCommand(DefaultEditorCommand.value());
        }
        component.setWorkingDirectory(template.getWorkingDirectory());
        return component;
    }

    private List<EnvironmentPortBinding> ports(ContainerTemplateRecord template, int slot, String componentType, CreateTrainingEnvironmentRequest request) {
        try {
            List<ContainerPortSpec> source = objectMapper.readValue(template.getPortsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ContainerPortSpec.class));
            List<EnvironmentPortBinding> result = new ArrayList<>();
            int index = 0;
            for (ContainerPortSpec port : source) {
                EnvironmentPortBinding binding = new EnvironmentPortBinding();
                binding.setContainerPort(port.getContainerPort());
                binding.setProtocol(port.getProtocol());
                Integer selected = "ANNOTATION".equals(componentType) ? request.getAnnotationHostPort() : (index == 0 ? request.getEditorVscodeHostPort() : index == 1 ? request.getEditorJupyterHostPort() : request.getEditorT100HostPort());
                binding.setHostPort(selected ==  null ? defaultHostPort(slot, componentType, index) : selected);
                validateHostPort(binding.getHostPort(), componentType, index, request.getEnvironmentType());
                index++;
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

    private List<EnvironmentPortBinding> poolPorts(String environmentId,ProcessingEnvironmentSlotRecord slot,
                                                   ContainerTemplateRecord template,String environmentType){
        try{
            List<ContainerPortSpec> source=objectMapper.readValue(template.getPortsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class,ContainerPortSpec.class));
            List<EnvironmentPortBinding> result=new ArrayList<>();
            for(ContainerPortSpec port:source){String serviceType=serviceType(template.getComponentType(),port.getContainerPort());
                EnvironmentPortAllocationRecord allocation=portPoolService.allocate(environmentId,slot.getSlotId(),slot.getAgentId(),environmentType,serviceType,template.getComponentType(),port.getContainerPort(),port.getProtocol());
                EnvironmentPortBinding binding=new EnvironmentPortBinding();binding.setContainerPort(port.getContainerPort());binding.setHostPort(allocation.getHostPort());binding.setProtocol(port.getProtocol());result.add(binding);}
            return result;
        }catch(IllegalArgumentException error){throw error;}catch(Exception error){throw new IllegalArgumentException("模板端口配置无效",error);}
    }
    private String serviceType(String componentType,int containerPort){if("ANNOTATION".equals(componentType))return "ANNOTATION";if(containerPort==9090)return "VSCODE";if(containerPort==8888)return "JUPYTER";if(containerPort==5000)return "T100";throw new IllegalArgumentException("代码编辑模板包含未知端口: "+containerPort);}

    private void validateHostPort(int port, String type, int index, String environmentType) {
        boolean competition = "COMPETITION".equalsIgnoreCase(environmentType);
        int[] allowed = "ANNOTATION".equals(type) ? (competition ? new int[]{8091,8092,8093,8094} : new int[]{8081,8082,8083,8084})
                : index == 0 ? (competition ? new int[]{9191,9192,9193,9194} : new int[]{9091,9092,9093,9094})
                : index == 1 ? (competition ? new int[]{9991,9992,9993,9994} : new int[]{8881,8882,8883,8884})
                : (competition ? new int[]{5501,5502,5503,5504} : new int[]{5001,5002,5003,5004});
        for(int candidate:allowed)if(port==candidate)return;
        throw new IllegalArgumentException("主机端口不在允许范围内: " + port);
    }

    private ContainerTemplateRecord optionalTemplate(String id, Integer version, String type) {
        if (id == null && version == null) return null;
        return requireTemplate(id, version, type);
    }

    private void verifyServerImage(String agentId, ContainerTemplateRecord template) {
        if (template == null || imageDeploymentMapper == null) return;
        ImageDeploymentRecord deployment = imageDeploymentMapper.selectLatestSucceeded(agentId,
                template.getComponentType());
        String expectedDigest=template.getImageDigest()==null?template.getImageReference():template.getImageDigest();
        if (deployment == null || !Objects.equals(expectedDigest, deployment.getTargetDigest())) {
            throw new IllegalArgumentException("处理服务器镜像版本与模板不一致，请先推送镜像版本");
        }
    }

    private void validateRequest(CreateTrainingEnvironmentRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId() < 1
                || request.getCourseId() != null && request.getCourseId().trim().isEmpty()
                || request.getAgentId() == null || request.getSlotNumber() == null
                || request.getSlotNumber() < 1 || request.getSlotNumber() > 4) {
            throw new IllegalArgumentException("实训环境分配参数不正确");
        }
        if (request.getEnvironmentType() == null) request.setEnvironmentType("COURSE");
        if (!"COURSE".equalsIgnoreCase(request.getEnvironmentType())
                && !"COMPETITION".equalsIgnoreCase(request.getEnvironmentType())) {
            throw new IllegalArgumentException("环境类型无效");
        }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("环境命令无法序列化", exception); }
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
