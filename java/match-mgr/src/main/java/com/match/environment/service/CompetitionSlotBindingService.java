package com.match.environment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.service.AgentAuditService;
import com.match.entity.User;
import com.match.environment.model.CompetitionSlotView;
import com.match.environment.model.EnvironmentPortBinding;
import com.match.environment.persistence.CompetitionEnvironmentMapper;
import com.match.environment.persistence.CompetitionEnvironmentRecord;
import com.match.environment.persistence.EnvironmentPortAllocationMapper;
import com.match.environment.persistence.EnvironmentPortAllocationRecord;
import com.match.environment.persistence.ProcessingEnvironmentSlotMapper;
import com.match.environment.persistence.ProcessingEnvironmentSlotRecord;
import com.match.mapper.UserMapper;
import com.match.mode.service.ModeConflictException;
import com.match.mode.service.ProcessingAgentModeGuard;
import com.match.security.AdminAccessException;
import com.match.security.ParticipantModeGuard;
import com.match.security.RoleGuard;
import com.match.security.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class CompetitionSlotBindingService {
    private static final int MAX_VERIFICATION_BYTES = 16 * 1024;

    private final ProcessingEnvironmentSlotMapper slotMapper;
    private final CompetitionEnvironmentMapper environmentMapper;
    private final ProcessingAgentModeGuard agentModeGuard;
    private final UserMapper userMapper;
    private final ProcessingAgentMapper agentMapper;
    private final EnvironmentPortAllocationMapper portMapper;
    private final RoleGuard roleGuard;
    private final ParticipantModeGuard participantModeGuard;
    private final AgentAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CompetitionSlotBindingService(ProcessingEnvironmentSlotMapper slotMapper,
                                         CompetitionEnvironmentMapper environmentMapper,
                                         ProcessingAgentModeGuard agentModeGuard,
                                         UserMapper userMapper,
                                         ProcessingAgentMapper agentMapper,
                                         EnvironmentPortAllocationMapper portMapper,
                                         RoleGuard roleGuard,
                                         ParticipantModeGuard participantModeGuard,
                                         AgentAuditService auditService,
                                         ObjectMapper objectMapper, Clock clock) {
        this.slotMapper = slotMapper;
        this.environmentMapper = environmentMapper;
        this.agentModeGuard = agentModeGuard;
        this.userMapper = userMapper;
        this.agentMapper = agentMapper;
        this.portMapper = portMapper;
        this.roleGuard = roleGuard;
        this.participantModeGuard = participantModeGuard;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public CompetitionSlotView bind(String slotId, int userId, User actor) {
        int actorUserId = requireAdmin(actor);
        participantModeGuard.requireAdministrativeTrainingMode();
        ProcessingEnvironmentSlotRecord slot = requireLockedSlot(slotId);
        requireIdleAgent(slot.getAgentId());
        CompetitionEnvironmentRecord environment = environmentMapper.selectBySlotForUpdate(slotId);
        requireReady(slot, environment);
        requireEnabledParticipant(userId);
        if (slotMapper.selectByAgentAndUserForUpdate(slot.getAgentId(), userId) != null) {
            throw conflict("COMPETITION_USER_ALREADY_BOUND",
                    "Participant is already bound on this processing Agent");
        }
        if (slot.getUserId() != null) {
            throw conflict("COMPETITION_SLOT_ALREADY_BOUND", "Competition slot is already bound");
        }
        LocalDateTime now = now();
        if (slotMapper.bindIfUnbound(slotId, userId, now) != 1) {
            throw conflict("COMPETITION_SLOT_BIND_CONFLICT", "Competition slot changed concurrently");
        }
        slot.setUserId(userId);
        auditService.recordSuccess("COMPETITION_SLOT_BOUND", actorUserId, slot.getAgentId(), null);
        return view(slot, environment, "READY");
    }

    @Transactional
    public CompetitionSlotView unbind(String slotId, int userId, User actor) {
        int actorUserId = requireAdmin(actor);
        participantModeGuard.requireAdministrativeTrainingMode();
        ProcessingEnvironmentSlotRecord slot = requireLockedSlot(slotId);
        if (!Objects.equals(slot.getUserId(), userId)) {
            throw conflict("COMPETITION_SLOT_BINDING_MISMATCH",
                    "Competition slot is not bound to that participant");
        }
        requireIdleAgent(slot.getAgentId());
        CompetitionEnvironmentRecord environment = environmentMapper.selectBySlotForUpdate(slotId);
        requireReady(slot, environment);
        LocalDateTime now = now();
        if (slotMapper.unbindIfBoundTo(slotId, userId, now) != 1) {
            throw conflict("COMPETITION_SLOT_UNBIND_CONFLICT", "Competition slot changed concurrently");
        }
        slot.setUserId(null);
        auditService.recordSuccess("COMPETITION_SLOT_UNBOUND", actorUserId, slot.getAgentId(), null);
        return view(slot, environment, "READY");
    }

    @Transactional(readOnly = true)
    public List<CompetitionSlotView> list(User actor) {
        requireAdmin(actor);
        List<CompetitionSlotView> result = new ArrayList<>();
        for (ProcessingEnvironmentSlotRecord slot : slotMapper.selectAll()) {
            CompetitionEnvironmentRecord environment = environmentMapper.selectBySlot(slot.getSlotId());
            String readinessCode = readinessCode(slot, environment);
            CompetitionSlotView view = view(slot, environment, readinessCode);
            includePortDetails(view, portMapper.selectBySlot(slot.getSlotId()));
            result.add(view);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CompetitionSlotView currentForUser(User participant) {
        int userId = requireParticipant(participant);
        List<ProcessingEnvironmentSlotRecord> bindings = slotMapper.selectByUser(userId);
        if (bindings == null || bindings.isEmpty()) {
            CompetitionSlotView unbound = new CompetitionSlotView();
            unbound.setReadiness("UNBOUND");
            return unbound;
        }
        if (bindings.size() != 1) {
            CompetitionSlotView degraded = new CompetitionSlotView();
            degraded.setReadiness("DEGRADED");
            degraded.setReadinessCode("COMPETITION_USER_MULTIPLE_BINDINGS");
            return degraded;
        }
        ProcessingEnvironmentSlotRecord slot = bindings.get(0);
        CompetitionEnvironmentRecord environment = environmentMapper.selectBySlot(slot.getSlotId());
        if (environment == null || !storedIdentityMatches(slot, environment)) {
            CompetitionSlotView degraded = view(slot, environment,
                    environment == null ? "COMPETITION_ENVIRONMENT_NOT_CREATED"
                            : "COMPETITION_ENVIRONMENT_IDENTITY_MISMATCH");
            degraded.setReadiness("DEGRADED");
            return degraded;
        }
        String runningVerification = verificationCode(environment, "RUNNING");
        boolean running = "RUNNING".equals(environment.getActualState())
                && "RUNNING".equals(environment.getAnnotationContainerState())
                && "RUNNING".equals(environment.getEditorContainerState())
                && "READY".equals(runningVerification);
        CompetitionSlotView result = view(slot, environment,
                running ? "RUNNING" : isStarting(environment)
                        ? "COMPETITION_ENVIRONMENT_STARTING" : runningVerification);
        if (!running) {
            result.setReadiness(isStarting(environment) ? "STARTING" : "DEGRADED");
            return result;
        }
        ProcessingAgentRecord agent = agentMapper.selectForManagement(slot.getAgentId());
        List<EnvironmentPortAllocationRecord> allocations = portMapper.selectBySlot(slot.getSlotId());
        Integer annotationPort = hostPort(allocations, "ANNOTATION", 8080 + slot.getSlotNumber());
        Integer editorPort = hostPort(allocations, "EDITOR", 9090 + slot.getSlotNumber());
        if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())
                || !isIpLiteral(agent.getPrimaryIp())
                || annotationPort == null || editorPort == null) {
            result.setReadiness("DEGRADED");
            result.setReadinessCode("COMPETITION_ENDPOINT_UNAVAILABLE");
            return result;
        }
        result.setReadiness("RUNNING");
        result.setAnnotationUrl(url(agent.getPrimaryIp(), annotationPort));
        result.setEditorUrl(url(agent.getPrimaryIp(), editorPort));
        return result;
    }

    private int requireAdmin(User actor) {
        if (actor == null || actor.getUserId() == null || actor.getUserId() < 1
                || !Boolean.TRUE.equals(actor.getEnabled())
                || roleGuard.roleOf(actor) != UserRole.ADMIN) {
            throw new AdminAccessException("仅普通管理员可以绑定比赛槽位");
        }
        return actor.getUserId();
    }

    private int requireParticipant(User participant) {
        if (participant == null || participant.getUserId() == null || participant.getUserId() < 1
                || !Boolean.TRUE.equals(participant.getEnabled())
                || roleGuard.roleOf(participant) != UserRole.USER) {
            throw new AdminAccessException("仅普通用户可以查看比赛环境");
        }
        return participant.getUserId();
    }

    private ProcessingEnvironmentSlotRecord requireLockedSlot(String slotId) {
        if (blank(slotId)) {
            throw new IllegalArgumentException("比赛槽位编号不能为空");
        }
        ProcessingEnvironmentSlotRecord slot = slotMapper.selectForUpdate(slotId);
        if (slot == null || !Objects.equals(slotId, slot.getSlotId())
                || blank(slot.getAgentId()) || slot.getSlotNumber() == null
                || slot.getSlotNumber() < 1 || slot.getSlotNumber() > 4) {
            throw conflict("COMPETITION_SLOT_NOT_FOUND", "Competition slot does not exist");
        }
        return slot;
    }

    private void requireIdleAgent(String agentId) {
        agentModeGuard.requireIdleForBinding(agentId);
    }

    private void requireEnabledParticipant(int userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getUserId(), userId)
                || !Boolean.TRUE.equals(user.getEnabled())
                || roleGuard.roleOf(user) != UserRole.USER) {
            throw conflict("COMPETITION_PARTICIPANT_NOT_AVAILABLE",
                    "Competition participant does not exist or is disabled");
        }
    }

    private void requireReady(ProcessingEnvironmentSlotRecord slot,
                              CompetitionEnvironmentRecord environment) {
        String code = readinessCode(slot, environment);
        if (!"READY".equals(code)) {
            throw conflict(code, "Competition container pair is not ready for binding");
        }
    }

    private String readinessCode(ProcessingEnvironmentSlotRecord slot,
                                 CompetitionEnvironmentRecord environment) {
        if (environment == null) {
            return "COMPETITION_ENVIRONMENT_NOT_CREATED";
        }
        if (!slotIdentityMatches(slot, environment)) {
            return "COMPETITION_ENVIRONMENT_IDENTITY_MISMATCH";
        }
        if (blank(environment.getAnnotationContainerName())
                || blank(environment.getEditorContainerName())
                || blank(environment.getAnnotationConfigFingerprint())
                || blank(environment.getEditorConfigFingerprint())) {
            return "COMPETITION_PAIR_INCOMPLETE";
        }
        if (!storedIdentityMatches(slot, environment)) {
            return "COMPETITION_ENVIRONMENT_IDENTITY_MISMATCH";
        }
        if (environment.getCurrentOperationId() != null) {
            return "COMPETITION_ENVIRONMENT_OPERATION_ACTIVE";
        }
        if (!"STOPPED".equals(environment.getDesiredState())
                || !"STOPPED".equals(environment.getActualState())
                || !"STOPPED".equals(environment.getAnnotationContainerState())
                || !"STOPPED".equals(environment.getEditorContainerState())) {
            return "COMPETITION_PAIR_NOT_STOPPED";
        }
        return verificationCode(environment, "STOPPED");
    }

    private String verificationCode(CompetitionEnvironmentRecord environment, String expectedState) {
        if (environment.getLastVerifiedAt() == null
                || blank(environment.getLastComponentResultsJson())
                || environment.getLastComponentResultsJson().getBytes(StandardCharsets.UTF_8).length
                > MAX_VERIFICATION_BYTES) {
            return "COMPETITION_PAIR_NOT_VERIFIED";
        }
        JsonNode pair;
        try {
            JsonNode root = objectMapper.readTree(environment.getLastComponentResultsJson());
            if (root == null || !root.isObject()) {
                return "COMPETITION_PAIR_NOT_VERIFIED";
            }
            pair = root.path("pair");
        } catch (Exception exception) {
            return "COMPETITION_PAIR_NOT_VERIFIED";
        }
        JsonNode annotation = pair.path("annotation");
        JsonNode editor = pair.path("editor");
        if (!exactText(annotation, "componentType", "ANNOTATION")
                || !exactText(editor, "componentType", "EDITOR")
                || !exactText(annotation, "containerName", environment.getAnnotationContainerName())
                || !exactText(editor, "containerName", environment.getEditorContainerName())) {
            return "COMPETITION_PAIR_IDENTITY_MISMATCH";
        }
        if (!exactText(annotation, "configFingerprint", environment.getAnnotationConfigFingerprint())
                || !exactText(editor, "configFingerprint", environment.getEditorConfigFingerprint())) {
            return "COMPETITION_PAIR_FINGERPRINT_MISMATCH";
        }
        if (!exactText(annotation, "state", expectedState)
                || !exactText(editor, "state", expectedState)) {
            return "COMPETITION_PAIR_NOT_STOPPED";
        }
        return "READY";
    }

    private boolean storedIdentityMatches(ProcessingEnvironmentSlotRecord slot,
                                          CompetitionEnvironmentRecord environment) {
        if (!slotIdentityMatches(slot, environment)) {
            return false;
        }
        String shortAgent = environment.getAgentId() != null && environment.getAgentId().length() >= 8
                ? environment.getAgentId().substring(0, 8) : "";
        String prefix = "xkp-comp-" + shortAgent + "-s" + environment.getSlotNumber();
        return Objects.equals("competition/slot-" + environment.getSlotNumber(),
                environment.getWorkspaceRelativePath())
                && Objects.equals(prefix + "-annotation", environment.getAnnotationContainerName())
                && Objects.equals(prefix + "-editor", environment.getEditorContainerName());
    }

    private boolean slotIdentityMatches(ProcessingEnvironmentSlotRecord slot,
                                        CompetitionEnvironmentRecord environment) {
        return slot != null && environment != null
                && Objects.equals(slot.getSlotId(), environment.getSlotId())
                && Objects.equals(slot.getAgentId(), environment.getAgentId())
                && Objects.equals(slot.getSlotNumber(), environment.getSlotNumber());
    }

    private boolean exactText(JsonNode node, String field, String expected) {
        JsonNode value = node.path(field);
        return value.isTextual() && Objects.equals(expected, value.asText());
    }

    private CompetitionSlotView view(ProcessingEnvironmentSlotRecord slot,
                                     CompetitionEnvironmentRecord environment,
                                     String readinessCode) {
        CompetitionSlotView view = new CompetitionSlotView();
        if (slot != null) {
            view.setSlotId(slot.getSlotId());
            view.setAgentId(slot.getAgentId());
            view.setSlotNumber(slot.getSlotNumber());
            view.setUserId(slot.getUserId());
        }
        view.setReadinessCode(readinessCode);
        if (environment == null) {
            return view;
        }
        view.setEnvironmentId(environment.getEnvironmentId());
        view.setAnnotationTemplateId(environment.getAnnotationTemplateId());
        view.setAnnotationTemplateVersion(environment.getAnnotationTemplateVersion());
        view.setAnnotationContainerName(environment.getAnnotationContainerName());
        view.setAnnotationContainerState(environment.getAnnotationContainerState());
        view.setAnnotationConfigFingerprint(environment.getAnnotationConfigFingerprint());
        view.setEditorTemplateId(environment.getEditorTemplateId());
        view.setEditorTemplateVersion(environment.getEditorTemplateVersion());
        view.setEditorContainerName(environment.getEditorContainerName());
        view.setEditorContainerState(environment.getEditorContainerState());
        view.setEditorConfigFingerprint(environment.getEditorConfigFingerprint());
        view.setWorkspaceRelativePath(environment.getWorkspaceRelativePath());
        view.setDesiredState(environment.getDesiredState());
        view.setActualState(environment.getActualState());
        view.setLastVerifiedAt(environment.getLastVerifiedAt());
        return view;
    }

    private void includePortDetails(CompetitionSlotView view,
                                    List<EnvironmentPortAllocationRecord> allocations) {
        List<EnvironmentPortBinding> annotation = new ArrayList<>();
        List<EnvironmentPortBinding> editor = new ArrayList<>();
        if (allocations != null) {
            for (EnvironmentPortAllocationRecord allocation : allocations) {
                EnvironmentPortBinding binding = new EnvironmentPortBinding();
                binding.setContainerPort(allocation.getContainerPort());
                binding.setHostPort(allocation.getHostPort());
                binding.setProtocol(allocation.getProtocol());
                if ("ANNOTATION".equals(allocation.getComponentType())) {
                    annotation.add(binding);
                } else if ("EDITOR".equals(allocation.getComponentType())) {
                    editor.add(binding);
                }
            }
        }
        view.setAnnotationPorts(Collections.unmodifiableList(annotation));
        view.setEditorPorts(Collections.unmodifiableList(editor));
    }

    private Integer hostPort(List<EnvironmentPortAllocationRecord> allocations, String type,
                             int expectedHostPort) {
        if (allocations == null) {
            return null;
        }
        for (EnvironmentPortAllocationRecord allocation : allocations) {
            if (type.equals(allocation.getComponentType())
                    && Objects.equals(allocation.getHostPort(), expectedHostPort)) {
                return allocation.getHostPort();
            }
        }
        return null;
    }

    private boolean isStarting(CompetitionEnvironmentRecord environment) {
        return "CREATING".equals(environment.getActualState())
                || "RESTORING".equals(environment.getActualState())
                || "STARTING".equals(environment.getActualState())
                || ("RUNNING".equals(environment.getDesiredState())
                && !"RUNNING".equals(environment.getActualState()));
    }

    private String url(String host, int port) {
        String normalizedHost = host.contains(":") ? "[" + host + "]" : host;
        return "http://" + normalizedHost + ":" + port;
    }

    private boolean isIpLiteral(String value) {
        if (blank(value)) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:]+")) {
                return false;
            }
            try {
                return InetAddress.getByName(value) instanceof Inet6Address;
            } catch (Exception exception) {
                return false;
            }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                if (part.isEmpty() || part.length() > 3
                        || Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ModeConflictException conflict(String code, String message) {
        return new ModeConflictException(code, message);
    }
}
