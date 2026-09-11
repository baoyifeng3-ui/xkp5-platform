package com.match.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.model.AgentCommandFinishedEvent;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.match.agent.web.AgentProtocolException;
import com.match.environment.service.EnvironmentOperationReconciler;
import com.match.mode.service.ModeTransitionReconciler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.regex.Pattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AgentCommandService {
    public void cancelEnvironmentCommands(String agentId, String environmentId) {
        if (agentId == null || environmentId == null) return;
        mapper.cancelEnvironmentCommands(agentId, environmentId, LocalDateTime.now(Clock.systemUTC()));
    }
    static final String SHUTDOWN_SERVER = "SHUTDOWN_SERVER";
    static final String OPEN_ROOT_TERMINAL = "OPEN_ROOT_TERMINAL";
    static final String UPGRADE_AGENT = "UPGRADE_AGENT";
    public static final String EXECUTE_TERMINAL_INPUT = "EXECUTE_TERMINAL_INPUT";
    public static final String DEPLOY_IMAGE = "DEPLOY_IMAGE";
    static final int COMMAND_VERSION = 1;
    static final int MAX_DELIVERY_ATTEMPTS = 5;
    static final long LEASE_SECONDS = 30;
    private static final int MAX_RESULT_MESSAGE_LENGTH = 512;
    private static final int MAX_RESULT_JSON_BYTES = 16 * 1024;
    private static final int MAX_ENVIRONMENT_PAYLOAD_BYTES = 3 * 1024;
    private static final int MAX_TERMINAL_PAYLOAD_BYTES = 1024;
    private static final Pattern SAFE_RESULT_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");

    private final ProcessingAgentCommandMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final EnvironmentOperationReconciler environmentReconciler;
    private final ModeTransitionReconciler modeTransitionReconciler;
    private final String terminalRelayBaseUrl;
    private final ApplicationEventPublisher eventPublisher;

    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                               AgentAuditService auditService, Clock clock) {
        this(mapper, objectMapper, auditService, clock, null,
                null, "wss://127.0.0.1:19147/terminal/v1/agent", true, event -> { });
    }

    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                               AgentAuditService auditService, Clock clock,
                               EnvironmentOperationReconciler environmentReconciler) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler,
                null, "wss://127.0.0.1:19147/terminal/v1/agent", true, event -> { });
    }

    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                               AgentAuditService auditService, Clock clock,
                               EnvironmentOperationReconciler environmentReconciler,
                               ModeTransitionReconciler modeTransitionReconciler) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler,
                modeTransitionReconciler, "wss://127.0.0.1:19147/terminal/v1/agent", true,
                event -> { });
    }

    @Autowired
    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                               AgentAuditService auditService, Clock clock,
                               EnvironmentOperationReconciler environmentReconciler,
                               @Lazy ModeTransitionReconciler modeTransitionReconciler,
                               @Value("${match.terminal.agent-relay-url}")
                               String terminalRelayBaseUrl,
                               Environment environment,
                               ApplicationEventPublisher eventPublisher) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler,
                modeTransitionReconciler, terminalRelayBaseUrl,
                isDevelopmentProfile(environment), eventPublisher);
    }

    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                               AgentAuditService auditService, Clock clock,
                               EnvironmentOperationReconciler environmentReconciler,
                               String terminalRelayBaseUrl, Environment environment,
                               ApplicationEventPublisher eventPublisher) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler, null,
                terminalRelayBaseUrl, isDevelopmentProfile(environment), eventPublisher);
    }

    AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                        AgentAuditService auditService, Clock clock,
                         EnvironmentOperationReconciler environmentReconciler,
                         String terminalRelayBaseUrl, boolean insecureTerminalRelayAllowed) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler,
                null, terminalRelayBaseUrl, insecureTerminalRelayAllowed, event -> { });
    }

    AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                        AgentAuditService auditService, Clock clock,
                        EnvironmentOperationReconciler environmentReconciler,
                        String terminalRelayBaseUrl, boolean insecureTerminalRelayAllowed,
                        ApplicationEventPublisher eventPublisher) {
        this(mapper, objectMapper, auditService, clock, environmentReconciler, null,
                terminalRelayBaseUrl, insecureTerminalRelayAllowed, eventPublisher);
    }

    AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper,
                        AgentAuditService auditService, Clock clock,
                        EnvironmentOperationReconciler environmentReconciler,
                        ModeTransitionReconciler modeTransitionReconciler,
                        String terminalRelayBaseUrl, boolean insecureTerminalRelayAllowed,
                        ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.clock = clock;
        this.environmentReconciler = environmentReconciler;
        this.modeTransitionReconciler = modeTransitionReconciler;
        this.terminalRelayBaseUrl = validateTerminalRelayBaseUrl(externalRelayUrl(terminalRelayBaseUrl),
                insecureTerminalRelayAllowed);
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentCommandView requestTerminalCommand(ProcessingAgentRecord agent, String sessionId,
                                                   Instant agentConnectionDeadline,
                                                   Instant absoluteExpiresAt,
                                                   Integer requesterUserId, String requesterRole) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) {
            throw new IllegalArgumentException("Processing server is disabled");
        }
        if (!"SUPER_ADMIN".equals(requesterRole)) {
            throw new IllegalArgumentException("Terminal command requester role is invalid");
        }
        String normalizedSessionId = requireUuid(sessionId);
        Instant now = clock.instant();
        if (agentConnectionDeadline == null || agentConnectionDeadline.getNano() != 0
                || absoluteExpiresAt == null || absoluteExpiresAt.getNano() != 0
                || !agentConnectionDeadline.isAfter(now)
                || agentConnectionDeadline.isAfter(now.plusSeconds(90))
                || !absoluteExpiresAt.isAfter(agentConnectionDeadline)
                || absoluteExpiresAt.isAfter(now.plusSeconds(7200))) {
            throw new IllegalArgumentException("Terminal command deadlines are invalid");
        }

        String dedupKey = agentId + ":" + OPEN_ROOT_TERMINAL;
        ProcessingAgentCommandRecord existing = mapper.selectActiveByDedup(agentId,
                OPEN_ROOT_TERMINAL, dedupKey);
        if (existing != null) {
            if (isOrphanedTerminalCommand(existing)) {
                mapper.cancelTerminalCommand(existing.getCommandId(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            } else {
            return requireMatchingTerminalCommand(existing, agentId, dedupKey, normalizedSessionId,
                    agentConnectionDeadline, absoluteExpiresAt);
            }
        }
        String payloadJson = terminalPayload(normalizedSessionId, agentConnectionDeadline,
                absoluteExpiresAt);
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_TERMINAL_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Terminal command payload is invalid");
        }
        LocalDateTime requestedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString());
        record.setAgentId(agentId);
        record.setCommandType(OPEN_ROOT_TERMINAL);
        record.setCommandVersion(COMMAND_VERSION);
        record.setPayloadJson(payloadJson);
        record.setState("PENDING");
        record.setActiveDedupKey(dedupKey);
        record.setRequesterUserId(requesterUserId);
        record.setRequesterRole(requesterRole);
        record.setCorrelationId(UUID.randomUUID().toString());
        record.setRequestedAt(requestedAt);
        record.setAvailableAt(requestedAt);
        record.setAttemptCount(0);
        record.setUpdatedAt(requestedAt);
        try {
            mapper.insert(record);
            return toView(record);
        } catch (DuplicateKeyException collision) {
            ProcessingAgentCommandRecord concurrent = mapper.selectActiveByDedup(agentId,
                    OPEN_ROOT_TERMINAL, dedupKey);
            if (concurrent != null) {
                return requireMatchingTerminalCommand(concurrent, agentId, dedupKey,
                        normalizedSessionId, agentConnectionDeadline, absoluteExpiresAt);
            }
            throw collision;
        }
    }

    public boolean hasRunningTerminalLease(String commandId, String agentId,
                                           String leaseToken, String sessionId) {
        if (commandId == null || agentId == null || leaseToken == null || sessionId == null) {
            return false;
        }
        ProcessingAgentCommandRecord record = mapper.selectRunningTerminalLeaseForUpdate(
                commandId, agentId, leaseToken, sessionId);
        if (record == null || !commandId.equals(record.getCommandId())
                || !agentId.equals(record.getAgentId())
                || !leaseToken.equals(record.getLeaseToken())
                || !"RUNNING".equals(record.getState())
                || !OPEN_ROOT_TERMINAL.equals(record.getCommandType())
                || record.getPayloadJson() == null) {
            return false;
        }
        try {
            JsonNode payload = objectMapper.readTree(record.getPayloadJson());
            return payload != null && payload.isObject()
                    && textEquals(payload, "sessionId", sessionId);
        } catch (IOException | IllegalArgumentException invalidPayload) {
            return false;
        }
    }

    private static String externalRelayUrl(String configured) {
        if (configured != null && !configured.matches("^wss?://(127\\.0\\.0\\.1|localhost)(:\\d+)?/.*")) {
            return configured;
        }
        String management = System.getenv("XKP_AGENT_MANAGEMENT_URL");
        if (management == null || management.trim().isEmpty()) return configured;
        String value = management.trim().replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://").replaceFirst("/+$", "");
        return value + "/terminal/v1/agent";
    }

    @Transactional
    public void cancelTerminalCommand(String commandId) {
        if (commandId != null && !commandId.trim().isEmpty()) {
            mapper.cancelTerminalCommand(commandId,
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
    }

    @Transactional
    public AgentCommandView requestShutdown(ProcessingAgentRecord agent, Integer requesterUserId,
                                            String requesterRole) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) {
            throw new IllegalArgumentException("Processing server is disabled");
        }
        ProcessingAgentCommandRecord existing = mapper.selectActive(agentId, SHUTDOWN_SERVER);
        if (existing != null) {
            return toView(existing);
        }

        LocalDateTime now = utcNow();
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString());
        record.setAgentId(agentId);
        record.setCommandType(SHUTDOWN_SERVER);
        record.setCommandVersion(COMMAND_VERSION);
        record.setPayloadJson("{}");
        record.setState("PENDING");
        record.setActiveDedupKey(agentId + ":" + SHUTDOWN_SERVER);
        record.setRequesterUserId(requesterUserId);
        record.setRequesterRole(requireRole(requesterRole));
        record.setCorrelationId(UUID.randomUUID().toString());
        record.setRequestedAt(now);
        record.setAvailableAt(now);
        record.setAttemptCount(0);
        record.setUpdatedAt(now);
        try {
            mapper.insert(record);
            return toView(record);
        } catch (DuplicateKeyException collision) {
            ProcessingAgentCommandRecord concurrent = mapper.selectActive(agentId, SHUTDOWN_SERVER);
            if (concurrent != null) {
                return toView(concurrent);
            }
            throw collision;
        }
    }

    @Transactional
    public AgentCommandView requestUpgrade(ProcessingAgentRecord agent, String targetVersion, String sha256,
                                           Integer requesterUserId, String requesterRole) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) throw new IllegalArgumentException("处理服务器不可用");
        ProcessingAgentCommandRecord existing = mapper.selectActive(agentId, UPGRADE_AGENT);
        if (existing != null) return toView(existing);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("targetVersion", targetVersion); payload.put("downloadPath", "/agent/v1/upgrade-binary"); payload.put("sha256", sha256);
        LocalDateTime now = utcNow();
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString()); record.setAgentId(agentId); record.setCommandType(UPGRADE_AGENT);
        record.setCommandVersion(COMMAND_VERSION); record.setPayloadJson(payload.toString()); record.setState("PENDING");
        record.setActiveDedupKey(agentId + ":" + UPGRADE_AGENT); record.setRequesterUserId(requesterUserId);
        record.setRequesterRole(requireRole(requesterRole)); record.setCorrelationId(UUID.randomUUID().toString());
        record.setRequestedAt(now); record.setAvailableAt(now); record.setAttemptCount(0); record.setUpdatedAt(now);
        mapper.insert(record); return toView(record);
    }

    @Transactional
    public AgentCommandView requestTerminalInput(ProcessingAgentRecord agent, String sessionId, String data,
                                                 Integer requesterUserId) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) throw new IllegalArgumentException("处理服务器不可用");
        ObjectNode payload = objectMapper.createObjectNode(); payload.put("sessionId", requireUuid(sessionId)); payload.put("data", data);
        LocalDateTime now = utcNow(); ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString()); record.setAgentId(agentId); record.setCommandType(EXECUTE_TERMINAL_INPUT);
        record.setCommandVersion(COMMAND_VERSION); record.setPayloadJson(payload.toString()); record.setState("PENDING");
        record.setRequesterUserId(requesterUserId); record.setRequesterRole("SUPER_ADMIN"); record.setCorrelationId(UUID.randomUUID().toString());
        record.setRequestedAt(now); record.setAvailableAt(now); record.setAttemptCount(0); record.setUpdatedAt(now); mapper.insert(record);
        return toView(record);
    }

    @Transactional
    public AgentCommandView requestEnvironmentCommand(ProcessingAgentRecord agent, String commandType,
                                                      String payloadJson, Integer requesterUserId,
                                                      String requesterRole, String dedupKey) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) {
            throw new IllegalArgumentException("Processing server is disabled");
        }
        if (!"CREATE_TRAINING_ENVIRONMENT".equals(commandType)
                && !"START_TRAINING_ENVIRONMENT".equals(commandType)
                && !"STOP_TRAINING_ENVIRONMENT".equals(commandType)
                && !"RESTORE_TRAINING_ENVIRONMENT".equals(commandType)
                && !"DELETE_TRAINING_ENVIRONMENT".equals(commandType)
                && !"DELIVER_COURSE_RESOURCE".equals(commandType)
                && !"TRANSFER_FILE".equals(commandType)
                && !"MODEL_WORKSPACE".equals(commandType)
                && !"DOCKER_INVENTORY_ACTION".equals(commandType)
                && !"CREATE_COMPETITION_ENVIRONMENT".equals(commandType)
                && !"START_COMPETITION_ENVIRONMENT".equals(commandType)
                && !"STOP_COMPETITION_ENVIRONMENT".equals(commandType)
                && !"RESTORE_COMPETITION_ENVIRONMENT".equals(commandType)
                && !"DELETE_COMPETITION_ENVIRONMENT".equals(commandType)) {
            throw new IllegalArgumentException("Environment command type is invalid");
        }
        if (payloadJson == null
                || payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_ENVIRONMENT_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Environment command payload is invalid");
        }
        ProcessingAgentCommandRecord existing = dedupKey == null ? null
                : mapper.selectActiveByDedup(agentId, commandType, dedupKey);
        if (existing != null) {
            return toView(existing);
        }
        LocalDateTime now = utcNow();
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString());
        record.setAgentId(agentId);
        record.setCommandType(commandType);
        record.setCommandVersion(COMMAND_VERSION);
        record.setPayloadJson(payloadJson);
        record.setState("PENDING");
        record.setActiveDedupKey(dedupKey);
        record.setRequesterUserId(requesterUserId);
        record.setRequesterRole(requireEnvironmentRole(requesterRole));
        record.setCorrelationId(UUID.randomUUID().toString());
        record.setRequestedAt(now);
        record.setAvailableAt(now);
        record.setAttemptCount(0);
        record.setUpdatedAt(now);
        try {
            mapper.insert(record);
            return toView(record);
        } catch (DuplicateKeyException collision) {
            ProcessingAgentCommandRecord concurrent = dedupKey == null ? null
                    : mapper.selectActiveByDedup(agentId, commandType, dedupKey);
            if (concurrent != null) {
                return toView(concurrent);
            }
            throw collision;
        }
    }

    @Transactional
    public AgentCommandView requestImageDeploymentCommand(ProcessingAgentRecord agent, String componentType,
                                                           String registryDigest, String updatePolicy,
                                                           String deploymentId, String idempotencyKey, Integer requesterUserId,
                                                           String requesterRole) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) {
            throw new IllegalArgumentException("Processing server is disabled");
        }
        if (!"ANNOTATION".equals(componentType) && !"EDITOR".equals(componentType)) {
            throw new IllegalArgumentException("Image component is invalid");
        }
        if (registryDigest == null || !Pattern.matches("^sha256:[0-9a-f]{64}$", registryDigest)) {
            throw new IllegalArgumentException("Image deployment requires an immutable digest");
        }
        if (!"UPDATE_CONTAINERS".equals(updatePolicy) && !"IMAGE_ONLY".equals(updatePolicy)) {
            throw new IllegalArgumentException("Image update policy is invalid");
        }
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Image deployment idempotency key is invalid");
        }
        String dedupKey = agentId + ":" + DEPLOY_IMAGE + ":" + idempotencyKey;
        if (dedupKey.length() > 128) throw new IllegalArgumentException("Image deployment idempotency key is invalid");
        ProcessingAgentCommandRecord existing = mapper.selectActiveByDedup(agentId, DEPLOY_IMAGE, dedupKey);
        if (existing != null) return toView(existing);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("agentId", agentId);
        payload.put("deploymentId", deploymentId);
        payload.put("componentType", componentType);
        payload.put("registryDigest", registryDigest);
        payload.put("updatePolicy", updatePolicy);
        payload.put("idempotencyKey", idempotencyKey);
        final String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image deployment payload is invalid", exception);
        }
        LocalDateTime now = utcNow();
        ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString()); record.setAgentId(agentId);
        record.setCommandType(DEPLOY_IMAGE); record.setCommandVersion(COMMAND_VERSION);
        record.setPayloadJson(payloadJson); record.setState("PENDING"); record.setActiveDedupKey(dedupKey);
        record.setRequesterUserId(requesterUserId); record.setRequesterRole(requireRole(requesterRole));
        record.setCorrelationId(UUID.randomUUID().toString()); record.setRequestedAt(now);
        record.setAvailableAt(now); record.setAttemptCount(0); record.setUpdatedAt(now);
        try {
            mapper.insert(record);
            return toView(record);
        } catch (DuplicateKeyException collision) {
            ProcessingAgentCommandRecord concurrent = mapper.selectActiveByDedup(agentId, DEPLOY_IMAGE, dedupKey);
            if (concurrent != null) return toView(concurrent);
            throw collision;
        }
    }

    @Transactional(readOnly = true)
    public List<AgentCommandView> recent(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent identity is required");
        }
        return mapper.selectRecent(agentId, 50).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<AgentCommandEnvelope> lease(ProcessingAgentRecord agent) {
        String agentId = requireAgentId(agent);
        // Terminal-capable Agents require second-precision UTC command timestamps.
        LocalDateTime now = utcNow().withNano(0);
        ProcessingAgentCommandRecord pending = mapper.selectNextForLease(agentId, now);
        if (pending == null) {
            return Optional.empty();
        }
        String leaseToken = UUID.randomUUID().toString();
        LocalDateTime leaseExpiresAt = now.plusSeconds(LEASE_SECONDS);
        if (mapper.markLeased(pending.getCommandId(), leaseToken, leaseExpiresAt, now) != 1) {
            return Optional.empty();
        }
        pending.setState("LEASED");
        pending.setLeaseToken(leaseToken);
        pending.setLeaseExpiresAt(leaseExpiresAt);
        pending.setDeliveredAt(now);
        pending.setAttemptCount(pending.getAttemptCount() == null ? 1 : pending.getAttemptCount() + 1);
        auditService.recordCommandSuccess("COMMAND_LEASED", null, agentId, pending.getCommandId());
        return Optional.of(toEnvelope(pending));
    }

    @Transactional
    public AgentCommandView start(ProcessingAgentRecord agent, String commandId,
                                  AgentCommandStartRequest request) {
        String agentId = requireAgentId(agent);
        String leaseToken = requireLeaseToken(request == null ? null : request.getLeaseToken());
        LocalDateTime now = utcNow();
        if (mapper.markRunning(commandId, agentId, leaseToken, now) == 1) {
            ProcessingAgentCommandRecord started = new ProcessingAgentCommandRecord();
            started.setCommandId(commandId);
            started.setAgentId(agentId);
            started.setState("RUNNING");
            started.setStartedAt(now);
            auditService.recordCommandSuccess("COMMAND_STARTED", null, agentId, commandId);
            return toView(started);
        }
        ProcessingAgentCommandRecord current = mapper.selectById(commandId);
        if (sameLease(current, agentId, leaseToken)
                && ("RUNNING".equals(current.getState()) || isTerminal(current.getState()))) {
            return toView(current);
        }
        throw leaseConflict();
    }

    @Transactional
    public AgentCommandView finish(ProcessingAgentRecord agent, String commandId,
                                   AgentCommandResultRequest request) {
        String agentId = requireAgentId(agent);
        ValidatedResult result = validateResult(request);
        LocalDateTime now = utcNow();
        if (result.success) {
            if (mapper.recordShutdownAccepted(commandId, agentId, result.leaseToken, now,
                    result.code, result.message, result.json) == 1) {
                ProcessingAgentCommandRecord accepted = new ProcessingAgentCommandRecord();
                accepted.setCommandId(commandId);
                accepted.setAgentId(agentId);
                accepted.setState("RUNNING");
                accepted.setResultCode(result.code);
                accepted.setResultMessage(result.message);
                accepted.setResultJson(result.json);
                auditService.recordCommandSuccess("SHUTDOWN_ACCEPTED", null, agentId, commandId);
                return toView(accepted);
            }
            ProcessingAgentCommandRecord current = mapper.selectById(commandId);
            if (sameLease(current, agentId, result.leaseToken)
                    && "RUNNING".equals(current.getState())
                    && Objects.equals(current.getResultCode(), result.code)
                    && Objects.equals(current.getResultMessage(), result.message)
                    && sameResultJson(current.getResultJson(), result.json)) {
                return toView(current);
            }
            if (sameLease(current, agentId, result.leaseToken)
                    && "SUCCEEDED".equals(current.getState())
                    && "OFFLINE_CONFIRMED".equals(current.getResultCode())) {
                return toView(current);
            }
            if (current != null && SHUTDOWN_SERVER.equals(current.getCommandType())) {
                throw leaseConflict();
            }
        }
        if (result.success && ("PULLED".equals(result.code) || "RUNNING".equals(result.code))) {
            if (mapper.markImageProgress(commandId, agentId, result.leaseToken, result.code,
                    result.message, result.json, now) == 1) {
                ProcessingAgentCommandRecord persisted = mapper.selectById(commandId);
                if (persisted != null) eventPublisher.publishEvent(new AgentCommandFinishedEvent(commandId, agentId,
                        persisted.getCommandType(), true, result.code, result.message, result.json));
                return toView(persisted);
            }
        }
        String terminalState = result.success ? "SUCCEEDED" : result.state;
        if (mapper.markTerminal(commandId, agentId, result.leaseToken, terminalState, now,
                result.code, result.message, result.json) == 1) {
            ProcessingAgentCommandRecord completed = new ProcessingAgentCommandRecord();
            completed.setCommandId(commandId);
            completed.setAgentId(agentId);
            completed.setState(terminalState);
            completed.setCompletedAt(now);
            completed.setResultCode(result.code);
            completed.setResultMessage(result.message);
            completed.setResultJson(result.json);
            if (result.success) {
                auditService.recordCommandSuccess("COMMAND_RESULT", null, agentId, commandId);
            } else {
                auditService.recordCommandFailure("COMMAND_RESULT", result.code, null, agentId, commandId);
            }
            boolean handledModeStep = modeTransitionReconciler != null
                    && modeTransitionReconciler.reconcileIfPresent(commandId, result.success,
                    result.code, result.message, result.json);
            if (!handledModeStep && environmentReconciler != null) {
                environmentReconciler.reconcile(commandId, result.success, result.code,
                        result.message, result.json);
            }
            ProcessingAgentCommandRecord persisted = mapper.selectById(commandId);
            if (persisted != null && Objects.equals(agentId, persisted.getAgentId())
                    && persisted.getCommandType() != null) {
                eventPublisher.publishEvent(new AgentCommandFinishedEvent(commandId, agentId,
                        persisted.getCommandType(), result.success, result.code, result.message, result.json));
            }
            return toView(completed);
        }
        ProcessingAgentCommandRecord current = mapper.selectById(commandId);
        if (sameLease(current, agentId, result.leaseToken)
                && "SUCCEEDED".equals(current.getState())
                && "OFFLINE_CONFIRMED".equals(current.getResultCode())) {
            return toView(current);
        }
        if (sameLease(current, agentId, result.leaseToken) && isTerminal(current.getState())
                && Objects.equals(current.getState(), terminalState)
                && Objects.equals(current.getResultCode(), result.code)
                && Objects.equals(current.getResultMessage(), result.message)
                && sameResultJson(current.getResultJson(), result.json)) {
            return toView(current);
        }
        throw leaseConflict();
    }

    private boolean sameResultJson(String stored, String received) {
        if (Objects.equals(stored, received)) {
            return true;
        }
        if (stored == null || received == null) {
            return false;
        }
        try {
            return objectMapper.readTree(stored).equals(objectMapper.readTree(received));
        } catch (IOException exception) {
            return false;
        }
    }

    private AgentCommandEnvelope toEnvelope(ProcessingAgentCommandRecord record) {
        AgentCommandEnvelope envelope = new AgentCommandEnvelope();
        envelope.setCommandId(record.getCommandId());
        envelope.setType(record.getCommandType());
        envelope.setVersion(record.getCommandVersion());
        envelope.setLeaseToken(record.getLeaseToken());
        envelope.setLeaseExpiresAt(record.getLeaseExpiresAt().toInstant(ZoneOffset.UTC));
        try {
            JsonNode payload = objectMapper.readTree(record.getPayloadJson());
            envelope.setPayload(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Stored Agent command payload is invalid", exception);
        }
        return envelope;
    }

    private AgentCommandView toView(ProcessingAgentCommandRecord record) {
        AgentCommandView view = new AgentCommandView();
        view.setCommandId(record.getCommandId());
        view.setAgentId(record.getAgentId());
        view.setType(record.getCommandType());
        view.setVersion(record.getCommandVersion());
        view.setState(record.getState());
        view.setAttemptCount(record.getAttemptCount());
        view.setRequestedAt(record.getRequestedAt());
        view.setDeliveredAt(record.getDeliveredAt());
        view.setStartedAt(record.getStartedAt());
        view.setCompletedAt(record.getCompletedAt());
        view.setResultCode(record.getResultCode());
        view.setResultMessage(record.getResultMessage());
        return view;
    }

    private String requireAgentId(ProcessingAgentRecord agent) {
        if (agent == null || agent.getAgentId() == null || agent.getAgentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent identity is required");
        }
        return agent.getAgentId();
    }

    private String requireRole(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new IllegalArgumentException("Command requester role is invalid");
        }
        return role;
    }

    private String requireEnvironmentRole(String role) {
        if ("USER".equals(role) || "SYSTEM".equals(role)) {
            return role;
        }
        return requireRole(role);
    }

    private ValidatedResult validateResult(AgentCommandResultRequest request) {
        if (request == null || request.getSuccess() == null) {
            throw new IllegalArgumentException("Command result success is required");
        }
        String leaseToken = requireLeaseToken(request.getLeaseToken());
        String code = request.getCode();
        if (code == null || !SAFE_RESULT_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Command result code is invalid");
        }
        String message = request.getMessage();
        if (message != null && message.length() > MAX_RESULT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Command result message is too long");
        }
        String json = null;
        if (request.getDetails() != null) {
            if (!request.getDetails().isObject()) {
                throw new IllegalArgumentException("Command result details must be an object");
            }
            try {
                json = objectMapper.writeValueAsString(request.getDetails());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Command result details are invalid", exception);
            }
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESULT_JSON_BYTES) {
                throw new IllegalArgumentException("Command result details are too large");
            }
        }
        return new ValidatedResult(leaseToken, request.getSuccess(), request.getSuccess() ? "RUNNING" : "FAILED",
                code, message, json);
    }

    private String requireLeaseToken(String leaseToken) {
        try {
            return UUID.fromString(leaseToken).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Command lease token is invalid");
        }
    }

    private String requireUuid(String value) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Terminal session identity is invalid");
        }
    }

    private String terminalPayload(String sessionId, Instant connectionDeadline, Instant absoluteExpiresAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sessionId", sessionId);
        payload.put("relayUrl", terminalRelayBaseUrl + "/" + sessionId);
        payload.put("agentConnectionDeadline", connectionDeadline.toString());
        payload.put("idleTimeoutSeconds", 600);
        payload.put("absoluteExpiresAt", absoluteExpiresAt.toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Terminal command payload cannot be serialized", exception);
        }
    }

    private AgentCommandView requireMatchingTerminalCommand(ProcessingAgentCommandRecord record,
                                                             String agentId,
                                                             String dedupKey,
                                                             String sessionId,
                                                             Instant connectionDeadline,
                                                             Instant absoluteExpiresAt) {
        try {
            String payloadJson = record.getPayloadJson();
            if (payloadJson == null
                    || payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_TERMINAL_PAYLOAD_BYTES) {
                throw terminalSessionConflict();
            }
            JsonNode payload = objectMapper.readTree(payloadJson);
            if (!OPEN_ROOT_TERMINAL.equals(record.getCommandType())
                    || !Integer.valueOf(COMMAND_VERSION).equals(record.getCommandVersion())
                    || !agentId.equals(record.getAgentId())
                    || !dedupKey.equals(record.getActiveDedupKey())
                    || payload == null || !payload.isObject() || payload.size() != 5
                    || !textEquals(payload, "sessionId", sessionId)
                    || !textEquals(payload, "relayUrl", terminalRelayBaseUrl + "/" + sessionId)
                    || !textEquals(payload, "agentConnectionDeadline", connectionDeadline.toString())
                    || payload.get("idleTimeoutSeconds") == null
                    || !payload.get("idleTimeoutSeconds").isIntegralNumber()
                    || !payload.get("idleTimeoutSeconds").canConvertToInt()
                    || payload.get("idleTimeoutSeconds").asInt() != 600
                    || !textEquals(payload, "absoluteExpiresAt", absoluteExpiresAt.toString())) {
                throw terminalSessionConflict();
            }
            return toView(record);
        } catch (IOException | IllegalArgumentException invalidPayload) {
            throw terminalSessionConflict();
        }
    }

    @Transactional
    public AgentCommandView requestImageFileDeploymentCommand(ProcessingAgentRecord agent, String imageName,
                                                               boolean overwrite, String deploymentId,
                                                               String idempotencyKey, Integer requesterUserId,
                                                               String requesterRole) {
        String agentId = requireAgentId(agent);
        if (mapper.selectEnabledAgentForUpdate(agentId) == null) throw new IllegalArgumentException("Processing server is disabled");
        if (imageName == null || !imageName.matches("[a-z0-9]+(?:[._/-][a-z0-9]+)*:[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Image name is invalid");
        String dedupKey = agentId + ":" + DEPLOY_IMAGE + ":" + idempotencyKey;
        ProcessingAgentCommandRecord existing = mapper.selectActiveByDedup(agentId, DEPLOY_IMAGE, dedupKey);
        if (existing != null) return toView(existing);
        ObjectNode payload = objectMapper.createObjectNode(); payload.put("agentId", agentId);
        payload.put("deploymentId", deploymentId); payload.put("imageName", imageName); payload.put("overwrite", overwrite);
        payload.put("idempotencyKey", idempotencyKey);
        String payloadJson; try { payloadJson = objectMapper.writeValueAsString(payload); }
        catch (IOException e) { throw new IllegalArgumentException("Image deployment payload is invalid", e); }
        LocalDateTime now = utcNow(); ProcessingAgentCommandRecord record = new ProcessingAgentCommandRecord();
        record.setCommandId(UUID.randomUUID().toString()); record.setAgentId(agentId); record.setCommandType(DEPLOY_IMAGE);
        record.setCommandVersion(COMMAND_VERSION); record.setPayloadJson(payloadJson); record.setState("PENDING");
        record.setActiveDedupKey(dedupKey); record.setRequesterUserId(requesterUserId); record.setRequesterRole(requireRole(requesterRole));
        record.setCorrelationId(UUID.randomUUID().toString()); record.setRequestedAt(now); record.setAvailableAt(now);
        record.setAttemptCount(0); record.setUpdatedAt(now); mapper.insert(record); return toView(record);
    }

    @Transactional
    public boolean failStaleImageDeployment(String commandId, LocalDateTime cutoff) {
        return mapper.failStaleImageDeployment(commandId, cutoff, utcNow()) == 1;
    }

    private boolean isOrphanedTerminalCommand(ProcessingAgentCommandRecord record) {
        return record.getPayloadJson() != null && record.getPayloadJson().contains("sessionId")
                && (record.getState() == null || "RUNNING".equals(record.getState()));
    }

    private boolean textEquals(JsonNode payload, String field, String expected) {
        JsonNode value = payload.get(field);
        return value != null && value.isTextual() && expected.equals(value.textValue());
    }

    private AgentProtocolException terminalSessionConflict() {
        return new AgentProtocolException("TERMINAL_COMMAND_SESSION_CONFLICT",
                "Active terminal command belongs to another session or has an invalid payload",
                HttpStatus.CONFLICT);
    }

    private static String validateTerminalRelayBaseUrl(String value, boolean insecureAllowed) {
        try {
            URI uri = new URI(value);
            boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
            boolean localInsecure = insecureAllowed && "ws".equalsIgnoreCase(uri.getScheme())
                    && isLoopbackHost(uri.getHost());
            if ((!secure && !localInsecure) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (!insecureAllowed && isLoopbackHost(uri.getHost()))) {
                throw new IllegalArgumentException("Terminal Agent relay URL is invalid");
            }
            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            String normalizedPath = uri.getPath();
            while (normalizedPath != null && normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            if (!"/terminal/v1/agent".equals(normalizedPath)
                    || !"/terminal/v1/agent".equals(uri.getRawPath())) {
                throw new IllegalArgumentException("Terminal Agent relay URL is invalid");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Terminal Agent relay URL is invalid", exception);
        }
    }

    private static boolean isDevelopmentProfile(Environment environment) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return profiles.contains("local") || profiles.contains("test");
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase();
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized) || "[::1]".equals(normalized);
    }

    private boolean sameLease(ProcessingAgentCommandRecord record, String agentId, String leaseToken) {
        return record != null && Objects.equals(agentId, record.getAgentId())
                && Objects.equals(leaseToken, record.getLeaseToken());
    }

    private boolean isTerminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private AgentProtocolException leaseConflict() {
        return new AgentProtocolException("COMMAND_LEASE_CONFLICT",
                "Command lease is stale or belongs to another Agent", HttpStatus.CONFLICT);
    }

    private LocalDateTime utcNow() {
        Instant now = clock.instant();
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }

    private static class ValidatedResult {
        private final String leaseToken;
        private final boolean success;
        private final String state;
        private final String code;
        private final String message;
        private final String json;

        private ValidatedResult(String leaseToken, boolean success, String state, String code, String message, String json) {
            this.leaseToken = leaseToken;
            this.success = success;
            this.state = state;
            this.code = code;
            this.message = message;
            this.json = json;
        }
    }
}
