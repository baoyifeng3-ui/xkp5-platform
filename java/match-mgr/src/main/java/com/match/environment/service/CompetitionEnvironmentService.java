package com.match.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentCommandService;
import com.match.entity.User;
import com.match.environment.model.CompetitionEnvironmentView;
import com.match.environment.model.ContainerPortSpec;
import com.match.environment.model.CreateCompetitionEnvironmentRequest;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.ContainerTemplateMapper;
import com.match.environment.persistence.ContainerTemplateRecord;
import com.match.environment.persistence.EnvironmentOperationMapper;
import com.match.environment.persistence.EnvironmentOperationRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.licensing.guard.LicenseGuard;
import com.match.security.AdminAccessException;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CompetitionEnvironmentService {
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");

    private final ProcessingAgentMapper agentMapper;
    private final ProcessingEnvironmentSlotMapper slotMapper;
    private final ContainerTemplateMapper templateMapper;
    private final CompetitionEnvironmentMapper environmentMapper;
    private final EnvironmentPortAllocationMapper portMapper;
    private final EnvironmentOperationMapper operationMapper;
    private final AgentCommandService commandService;
    private final EnvironmentCommandFactory commandFactory;
    private final LicenseGuard licenseGuard;
    private final RoleGuard roleGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CompetitionEnvironmentService(ProcessingAgentMapper agentMapper,
                                         ProcessingEnvironmentSlotMapper slotMapper,
                                         ContainerTemplateMapper templateMapper,
                                         CompetitionEnvironmentMapper environmentMapper,
                                         EnvironmentPortAllocationMapper portMapper,
                                         EnvironmentOperationMapper operationMapper,
                                         AgentCommandService commandService,
                                         EnvironmentCommandFactory commandFactory,
                                         LicenseGuard licenseGuard, RoleGuard roleGuard,
                                         ObjectMapper objectMapper, Clock clock) {
        this.agentMapper = agentMapper;
        this.slotMapper = slotMapper;
        this.templateMapper = templateMapper;
        this.environmentMapper = environmentMapper;
        this.portMapper = portMapper;
        this.operationMapper = operationMapper;
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.licenseGuard = licenseGuard;
        this.roleGuard = roleGuard;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public CompetitionEnvironmentView create(CreateCompetitionEnvironmentRequest request, User actor) {
        int actorUserId = requireSuperAdmin(actor);
        licenseGuard.requireActive();
        validateRequest(request);
        ProcessingAgentRecord agent = requireAgent(request.getAgentId());
        ProcessingEnvironmentSlotRecord slot = requireSlot(request.getAgentId(), request.getSlotNumber());
        if (environmentMapper.selectBySlotForUpdate(slot.getSlotId()) != null) {
            throw new IllegalArgumentException("比赛环境槽位已存在");
        }
        ContainerTemplateRecord annotation = requireTemplate(request.getAnnotationTemplateId(),
                request.getAnnotationTemplateVersion(), "ANNOTATION");
        ContainerTemplateRecord editor = requireTemplate(request.getEditorTemplateId(),
                request.getEditorTemplateVersion(), "EDITOR");
        List<EnvironmentPortAllocationRecord> existingAllocations = portMapper.selectBySlot(slot.getSlotId());
        reservePorts(slot, annotation, existingAllocations);
        reservePorts(slot, editor, existingAllocations);

        String environmentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        String prefix = "xkp-comp-" + agentShort(agent.getAgentId()) + "-s" + slot.getSlotNumber();
        LocalDateTime now = now();
        CompetitionEnvironmentRecord environment = new CompetitionEnvironmentRecord();
        environment.setEnvironmentId(environmentId);
        environment.setAgentId(agent.getAgentId());
        environment.setSlotId(slot.getSlotId());
        environment.setSlotNumber(slot.getSlotNumber());
        environment.setAnnotationTemplateId(annotation.getTemplateId());
        environment.setAnnotationTemplateVersion(annotation.getTemplateVersion());
        environment.setAnnotationConfigFingerprint(annotation.getConfigFingerprint());
        environment.setEditorTemplateId(editor.getTemplateId());
        environment.setEditorTemplateVersion(editor.getTemplateVersion());
        environment.setEditorConfigFingerprint(editor.getConfigFingerprint());
        environment.setWorkspaceRelativePath("competition/slot-" + slot.getSlotNumber());
        environment.setDesiredState("STOPPED");
        environment.setActualState("CREATING");
        environment.setAnnotationContainerName(prefix + "-annotation");
        environment.setAnnotationContainerState("UNKNOWN");
        environment.setEditorContainerName(prefix + "-editor");
        environment.setEditorContainerState("UNKNOWN");
        environment.setCurrentOperationId(operationId);
        environment.setLockVersion(0L);
        environment.setCreatedBy(actorUserId);
        environment.setCreatedAt(now);
        environment.setUpdatedBy(actorUserId);
        environment.setUpdatedAt(now);
        environmentMapper.insert(environment);

        EnvironmentOperationRecord operation = operation(environmentId, operationId,
                "CREATE", actorUserId, now);
        operationMapper.insert(operation);
        AgentCommandView command = dispatch(agent, environment, operation,
                "CREATE_COMPETITION_ENVIRONMENT", "CREATE", actorUserId);
        return view(environment, slot.getUserId(), operationId, command);
    }

    @Transactional
    public CompetitionEnvironmentView restore(String environmentId, User actor) {
        int actorUserId = requireSuperAdmin(actor);
        licenseGuard.requireActive();
        String normalizedId = requireUuid(environmentId, "比赛环境编号无效");
        CompetitionEnvironmentRecord environment = environmentMapper.selectForUpdate(normalizedId);
        if (environment == null) {
            throw new IllegalArgumentException("比赛环境不存在");
        }
        if (environment.getCurrentOperationId() != null
                || operationMapper.selectActive(environment.getEnvironmentId()) != null) {
            throw new IllegalArgumentException("比赛环境正在执行其他操作");
        }
        ProcessingAgentRecord agent = requireAgent(environment.getAgentId());
        ProcessingEnvironmentSlotRecord slot = requireSlot(environment.getAgentId(),
                environment.getSlotNumber());
        requireStoredIdentity(environment, slot);
        requirePinnedTemplate(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion(), "ANNOTATION",
                environment.getAnnotationConfigFingerprint());
        requirePinnedTemplate(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), "EDITOR",
                environment.getEditorConfigFingerprint());

        String operationId = UUID.randomUUID().toString();
        LocalDateTime now = now();
        if (environmentMapper.compareAndSetState(environment.getEnvironmentId(),
                requireVersion(environment.getLockVersion()), "STOPPED", "RESTORING",
                operationId, actorUserId, now) != 1) {
            throw new IllegalArgumentException("比赛环境状态已变化，请刷新后重试");
        }
        environment.setDesiredState("STOPPED");
        environment.setActualState("RESTORING");
        environment.setCurrentOperationId(operationId);
        environment.setLockVersion(environment.getLockVersion() + 1L);
        EnvironmentOperationRecord operation = operation(environment.getEnvironmentId(), operationId,
                "RESTORE", actorUserId, now);
        operationMapper.insert(operation);
        AgentCommandView command = dispatch(agent, environment, operation,
                "RESTORE_COMPETITION_ENVIRONMENT", "RESTORE", actorUserId);
        return view(environment, slot.getUserId(), operationId, command);
    }

    @Transactional(readOnly = true)
    public CompetitionEnvironmentView get(String environmentId, User actor) {
        requireSuperAdmin(actor);
        CompetitionEnvironmentRecord environment = environmentMapper.selectById(
                requireUuid(environmentId, "比赛环境编号无效"));
        if (environment == null) {
            throw new IllegalArgumentException("比赛环境不存在");
        }
        ProcessingEnvironmentSlotRecord slot = slotMapper.selectById(environment.getSlotId());
        requireStoredIdentity(environment, slot);
        return view(environment, slot.getUserId(), environment.getCurrentOperationId(), null);
    }

    @Transactional(readOnly = true)
    public List<CompetitionEnvironmentView> list(User actor) {
        requireSuperAdmin(actor);
        List<CompetitionEnvironmentRecord> environments = environmentMapper.selectAllEnvironments();
        List<CompetitionEnvironmentView> views = new ArrayList<>();
        if (environments == null || environments.isEmpty()) {
            return views;
        }
        DiagnosticData diagnostics = diagnosticData(environments);
        for (CompetitionEnvironmentRecord environment : environments) {
            ProcessingEnvironmentSlotRecord slot = diagnostics.slots.get(environment.getSlotId());
            requireStoredIdentity(environment, slot);
            views.add(view(environment, slot.getUserId(), environment.getCurrentOperationId(),
                    null, diagnostics));
        }
        return views;
    }

    private AgentCommandView dispatch(ProcessingAgentRecord agent, CompetitionEnvironmentRecord environment,
                                      EnvironmentOperationRecord operation, String commandType,
                                      String operationType, int actorUserId) {
        boolean control = "START".equals(operationType) || "STOP".equals(operationType);
        AgentCommandView command = commandService.requestEnvironmentCommand(agent, commandType,
                control
                        ? commandFactory.createControlPayloadJson(environment, operation.getOperationId())
                        : commandFactory.createPayloadJson(environment, operation.getOperationId()), actorUserId,
                "SUPER_ADMIN", environment.getEnvironmentId() + ":" + operationType);
        operation.setCommandId(command.getCommandId());
        operationMapper.updateById(operation);
        return command;
    }

    private EnvironmentOperationRecord operation(String environmentId, String operationId,
                                                 String type, int actorUserId, LocalDateTime now) {
        EnvironmentOperationRecord operation = new EnvironmentOperationRecord();
        operation.setOperationId(operationId);
        operation.setEnvironmentId(environmentId);
        operation.setOperationType(type);
        operation.setActorUserId(actorUserId);
        operation.setActorRole("SUPER_ADMIN");
        operation.setState("PENDING");
        operation.setActiveOperationKey(environmentId + ":" + type);
        operation.setCorrelationId(UUID.randomUUID().toString());
        operation.setRequestedAt(now);
        operation.setUpdatedAt(now);
        return operation;
    }

    private void validateRequest(CreateCompetitionEnvironmentRequest request) {
        if (request == null || request.getSlotNumber() == null
                || request.getSlotNumber() < 1 || request.getSlotNumber() > 4
                || request.getAnnotationTemplateVersion() == null
                || request.getAnnotationTemplateVersion() < 1
                || request.getEditorTemplateVersion() == null
                || request.getEditorTemplateVersion() < 1) {
            throw new IllegalArgumentException("比赛环境参数不正确");
        }
        requireUuid(request.getAgentId(), "处理服务器编号无效");
        requireUuid(request.getAnnotationTemplateId(), "标注模板编号无效");
        requireUuid(request.getEditorTemplateId(), "编辑器模板编号无效");
    }

    private ProcessingAgentRecord requireAgent(String agentId) {
        ProcessingAgentRecord agent = agentMapper.selectForManagement(agentId);
        if (agent == null || !agentId.equals(agent.getAgentId())
                || !Boolean.TRUE.equals(agent.getEnabled()) || agent.getRemovedAt() != null) {
            throw new IllegalArgumentException("处理服务器不可用");
        }
        return agent;
    }

    private ProcessingEnvironmentSlotRecord requireSlot(String agentId, Integer slotNumber) {
        ProcessingEnvironmentSlotRecord slot = slotMapper.selectByAgentAndNumberForUpdate(agentId, slotNumber);
        if (slot == null || slot.getSlotId() == null || !agentId.equals(slot.getAgentId())
                || !slotNumber.equals(slot.getSlotNumber())) {
            throw new IllegalArgumentException("处理服务器槽位不存在或身份不匹配");
        }
        requireUuid(slot.getSlotId(), "处理服务器槽位身份无效");
        return slot;
    }

    private ContainerTemplateRecord requireTemplate(String id, Integer version, String type) {
        ContainerTemplateRecord template = requireTemplateVersion(id, version, type);
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new IllegalArgumentException("比赛环境固定模板版本不可用");
        }
        return template;
    }

    private ContainerTemplateRecord requireTemplateVersion(String id, Integer version, String type) {
        ContainerTemplateRecord template = templateMapper.selectVersion(id, version);
        if (template == null || !id.equals(template.getTemplateId())
                || !version.equals(template.getTemplateVersion())
                || !type.equals(template.getComponentType())
                || template.getConfigFingerprint() == null
                || !FINGERPRINT.matcher(template.getConfigFingerprint()).matches()) {
            throw new IllegalArgumentException("比赛环境固定模板版本不可用");
        }
        return template;
    }

    private void requirePinnedTemplate(String id, Integer version, String type, String fingerprint) {
        ContainerTemplateRecord template = requireTemplateVersion(id, version, type);
        if (!Objects.equals(fingerprint, template.getConfigFingerprint())) {
            throw new IllegalArgumentException("比赛环境模板指纹已变化");
        }
    }

    private void requireStoredIdentity(CompetitionEnvironmentRecord environment,
                                       ProcessingEnvironmentSlotRecord slot) {
        if (environment == null || slot == null
                || !Objects.equals(environment.getSlotId(), slot.getSlotId())
                || !Objects.equals(environment.getAgentId(), slot.getAgentId())
                || !Objects.equals(environment.getSlotNumber(), slot.getSlotNumber())) {
            throw new IllegalStateException("比赛环境槽位身份不一致");
        }
        String prefix = "xkp-comp-" + agentShort(environment.getAgentId())
                + "-s" + environment.getSlotNumber();
        if (!Objects.equals("competition/slot-" + environment.getSlotNumber(),
                environment.getWorkspaceRelativePath())
                || !Objects.equals(prefix + "-annotation", environment.getAnnotationContainerName())
                || !Objects.equals(prefix + "-editor", environment.getEditorContainerName())) {
            throw new IllegalStateException("比赛环境容器身份不一致");
        }
    }

    private void reservePorts(ProcessingEnvironmentSlotRecord slot, ContainerTemplateRecord template,
                              List<EnvironmentPortAllocationRecord> existingAllocations) {
        int index = 0;
        for (ContainerPortSpec port : templatePorts(template)) {
            EnvironmentPortAllocationRecord slotAllocation = findSlotAllocation(
                    existingAllocations, template.getComponentType(), port);
            if (slotAllocation != null) {
                if (!Objects.equals(slot.getAgentId(), slotAllocation.getAgentId())
                        || !Objects.equals(slot.getSlotId(), slotAllocation.getSlotId())) {
                    throw new IllegalStateException("environment port allocation identity is invalid");
                }
                index++;
                continue;
            }
            int hostPort = defaultHostPort(slot.getSlotNumber(), template.getComponentType(), index++);
            EnvironmentPortAllocationRecord existing = portMapper.selectAgentPortForUpdate(
                    slot.getAgentId(), hostPort, port.getProtocol());
            if (existing != null && Objects.equals(existing.getSlotId(), slot.getSlotId())
                    && Objects.equals(existing.getAgentId(), slot.getAgentId())
                    && Objects.equals(existing.getComponentType(), template.getComponentType())
                    && Objects.equals(existing.getContainerPort(), port.getContainerPort())) {
                continue;
            }
            if (existing != null) {
                throw new IllegalArgumentException("主机端口已被占用: " + hostPort);
            }
            EnvironmentPortAllocationRecord allocation = new EnvironmentPortAllocationRecord();
            allocation.setAllocationId(UUID.randomUUID().toString());
            allocation.setSlotId(slot.getSlotId());
            allocation.setAgentId(slot.getAgentId());
            allocation.setComponentType(template.getComponentType());
            allocation.setContainerPort(port.getContainerPort());
            allocation.setHostPort(hostPort);
            allocation.setProtocol(port.getProtocol());
            allocation.setCreatedAt(now());
            portMapper.insert(allocation);
        }
    }

    private EnvironmentPortAllocationRecord findSlotAllocation(
            List<EnvironmentPortAllocationRecord> allocations, String componentType,
            ContainerPortSpec port) {
        if (allocations == null) {
            return null;
        }
        for (EnvironmentPortAllocationRecord allocation : allocations) {
            if (Objects.equals(componentType, allocation.getComponentType())
                    && Objects.equals(port.getContainerPort(), allocation.getContainerPort())
                    && Objects.equals(port.getProtocol(), allocation.getProtocol())) {
                return allocation;
            }
        }
        return null;
    }

    private List<ContainerPortSpec> templatePorts(ContainerTemplateRecord template) {
        try {
            List<ContainerPortSpec> ports = objectMapper.readValue(template.getPortsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ContainerPortSpec.class));
            if (ports == null || ports.isEmpty()) {
                throw new IllegalArgumentException("模板端口配置无效");
            }
            return ports;
        } catch (Exception exception) {
            throw new IllegalArgumentException("模板端口配置无效", exception);
        }
    }

    private int defaultHostPort(int slot, String componentType, int index) {
        if ("ANNOTATION".equals(componentType)) {
            return index == 0 ? 8080 + slot : 18000 + slot * 32 + index;
        }
        if (index == 0) {
            return 9090 + slot;
        }
        if (index == 1) {
            return 8880 + slot;
        }
        return index == 2 ? 5000 + slot : 28000 + slot * 32 + index;
    }

    private int requireSuperAdmin(User actor) {
        if (actor == null || actor.getUserId() == null || actor.getUserId() < 1
                || !Boolean.TRUE.equals(actor.getEnabled())
                || roleGuard.roleOf(actor) != UserRole.SUPER_ADMIN) {
            throw new AdminAccessException("仅超级管理员可以维护比赛环境");
        }
        return actor.getUserId();
    }

    private long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalStateException("比赛环境版本无效");
        }
        return version;
    }

    private String requireUuid(String value, String message) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private String agentShort(String agentId) {
        return requireUuid(agentId, "处理服务器编号无效").substring(0, 8);
    }

    private CompetitionEnvironmentView view(CompetitionEnvironmentRecord environment, Integer userId,
                                            String operationId, AgentCommandView command) {
        return view(environment, userId, operationId, command, null);
    }

    private CompetitionEnvironmentView view(CompetitionEnvironmentRecord environment, Integer userId,
                                            String operationId, AgentCommandView command,
                                            DiagnosticData diagnostics) {
        CompetitionEnvironmentView view = new CompetitionEnvironmentView();
        view.setEnvironmentId(environment.getEnvironmentId());
        view.setOperationId(operationId);
        view.setCommand(command);
        view.setAgentId(environment.getAgentId());
        view.setSlotId(environment.getSlotId());
        view.setSlotNumber(environment.getSlotNumber());
        view.setUserId(userId);
        EnvironmentOperationRecord latestOperation = diagnostics == null
                ? latestOperation(environment.getEnvironmentId())
                : diagnostics.operations.get(environment.getEnvironmentId());
        populateReadiness(view, environment, latestOperation);
        view.setAnnotationTemplateId(environment.getAnnotationTemplateId());
        view.setAnnotationTemplateVersion(environment.getAnnotationTemplateVersion());
        view.setAnnotationImageReference(imageReference(environment.getAnnotationTemplateId(),
                environment.getAnnotationTemplateVersion(), "ANNOTATION", diagnostics));
        view.setAnnotationConfigFingerprint(environment.getAnnotationConfigFingerprint());
        view.setEditorTemplateId(environment.getEditorTemplateId());
        view.setEditorTemplateVersion(environment.getEditorTemplateVersion());
        view.setEditorImageReference(imageReference(environment.getEditorTemplateId(),
                environment.getEditorTemplateVersion(), "EDITOR", diagnostics));
        view.setEditorConfigFingerprint(environment.getEditorConfigFingerprint());
        List<EnvironmentPortAllocationRecord> allocations = diagnostics == null
                ? portMapper.selectBySlot(environment.getSlotId())
                : diagnostics.ports.get(environment.getSlotId());
        view.setAnnotationPorts(portBindings(allocations, "ANNOTATION"));
        view.setEditorPorts(portBindings(allocations, "EDITOR"));
        view.setWorkspaceRelativePath(environment.getWorkspaceRelativePath());
        view.setDesiredState(environment.getDesiredState());
        view.setActualState(environment.getActualState());
        view.setAnnotationContainerName(environment.getAnnotationContainerName());
        view.setAnnotationContainerState(environment.getAnnotationContainerState());
        view.setEditorContainerName(environment.getEditorContainerName());
        view.setEditorContainerState(environment.getEditorContainerState());
        view.setLastVerifiedAt(environment.getLastVerifiedAt());
        view.setLastComponentResultsJson(environment.getLastComponentResultsJson());
        return view;
    }

    private EnvironmentOperationRecord latestOperation(String environmentId) {
        List<EnvironmentOperationRecord> recent = operationMapper.selectRecent(environmentId, 1);
        return recent == null || recent.isEmpty() ? null : recent.get(0);
    }

    private void populateReadiness(CompetitionEnvironmentView view,
                                   CompetitionEnvironmentRecord environment,
                                   EnvironmentOperationRecord operation) {
        String actual = environment.getActualState();
        if (Arrays.asList("CREATING", "STARTING", "STOPPING", "RESTORING",
                "WAITING_DEPENDENCY").contains(actual)) {
            view.setReadiness("STARTING");
            view.setReadinessCode("COMPETITION_ENVIRONMENT_" + actual);
            return;
        }
        if ("ERROR".equals(actual) || "DEGRADED".equals(actual)) {
            view.setReadiness("DEGRADED");
            view.setReadinessCode(operation != null && operation.getResultCode() != null
                    ? operation.getResultCode() : "COMPETITION_ENVIRONMENT_" + actual);
            view.setFailureSummary(operation == null ? null : operation.getResultMessage());
            return;
        }
        if (("RUNNING".equals(actual) || "STOPPED".equals(actual))
                && actual.equals(environment.getAnnotationContainerState())
                && actual.equals(environment.getEditorContainerState())) {
            view.setReadiness("READY");
            view.setReadinessCode("READY");
            return;
        }
        view.setReadiness("DEGRADED");
        view.setReadinessCode("COMPETITION_COMPONENT_STATE_MISMATCH");
        view.setFailureSummary(operation == null ? null : operation.getResultMessage());
    }

    private String imageReference(String templateId, Integer version, String componentType,
                                  DiagnosticData diagnostics) {
        ContainerTemplateRecord template = diagnostics == null
                ? templateMapper.selectVersion(templateId, version)
                : diagnostics.templates.get(templateKey(templateId, version));
        if (template == null || !Objects.equals(templateId, template.getTemplateId())
                || !Objects.equals(version, template.getTemplateVersion())
                || !Objects.equals(componentType, template.getComponentType())) {
            return null;
        }
        return template.getImageReference();
    }

    private List<EnvironmentPortBinding> portBindings(
            List<EnvironmentPortAllocationRecord> allocations, String componentType) {
        List<EnvironmentPortBinding> result = new ArrayList<>();
        if (allocations != null) {
            for (EnvironmentPortAllocationRecord allocation : allocations) {
                if (!componentType.equals(allocation.getComponentType())) {
                    continue;
                }
                EnvironmentPortBinding binding = new EnvironmentPortBinding();
                binding.setContainerPort(allocation.getContainerPort());
                binding.setHostPort(allocation.getHostPort());
                binding.setProtocol(allocation.getProtocol());
                result.add(binding);
            }
        }
        result.sort(Comparator.comparing(EnvironmentPortBinding::getContainerPort)
                .thenComparing(EnvironmentPortBinding::getProtocol));
        return result;
    }

    private DiagnosticData diagnosticData(List<CompetitionEnvironmentRecord> environments) {
        DiagnosticData data = new DiagnosticData();
        List<String> environmentIds = new ArrayList<>();
        List<String> slotIds = new ArrayList<>();
        for (CompetitionEnvironmentRecord environment : environments) {
            environmentIds.add(environment.getEnvironmentId());
            slotIds.add(environment.getSlotId());
        }
        List<ProcessingEnvironmentSlotRecord> slots = slotMapper.selectAll();
        if (slots != null) {
            for (ProcessingEnvironmentSlotRecord slot : slots) {
                data.slots.put(slot.getSlotId(), slot);
            }
        }
        List<ContainerTemplateRecord> templates = templateMapper.selectAllVersions();
        if (templates != null) {
            for (ContainerTemplateRecord template : templates) {
                data.templates.put(templateKey(template.getTemplateId(),
                        template.getTemplateVersion()), template);
            }
        }
        List<EnvironmentPortAllocationRecord> ports = portMapper.selectBySlots(slotIds);
        if (ports != null) {
            for (EnvironmentPortAllocationRecord port : ports) {
                data.ports.computeIfAbsent(port.getSlotId(), ignored -> new ArrayList<>()).add(port);
            }
        }
        List<EnvironmentOperationRecord> operations =
                operationMapper.selectLatestByEnvironments(environmentIds);
        if (operations != null) {
            for (EnvironmentOperationRecord operation : operations) {
                data.operations.put(operation.getEnvironmentId(), operation);
            }
        }
        return data;
    }

    private String templateKey(String templateId, Integer version) {
        return templateId + ":" + version;
    }

    private static final class DiagnosticData {
        private final Map<String, ProcessingEnvironmentSlotRecord> slots = new HashMap<>();
        private final Map<String, ContainerTemplateRecord> templates = new HashMap<>();
        private final Map<String, List<EnvironmentPortAllocationRecord>> ports = new HashMap<>();
        private final Map<String, EnvironmentOperationRecord> operations = new HashMap<>();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
