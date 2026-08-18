package com.match.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandResultRequest;
import com.match.agent.model.AgentCommandStartRequest;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.match.agent.web.AgentProtocolException;

import java.io.IOException;
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

@Service
public class AgentCommandService {
    static final String SHUTDOWN_SERVER = "SHUTDOWN_SERVER";
    static final int COMMAND_VERSION = 1;
    static final int MAX_DELIVERY_ATTEMPTS = 5;
    static final long LEASE_SECONDS = 30;
    private static final int MAX_RESULT_MESSAGE_LENGTH = 512;
    private static final int MAX_RESULT_JSON_BYTES = 16 * 1024;
    private static final Pattern SAFE_RESULT_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");

    private final ProcessingAgentCommandMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentCommandService(ProcessingAgentCommandMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AgentCommandView requestShutdown(ProcessingAgentRecord agent, Integer requesterUserId,
                                            String requesterRole) {
        String agentId = requireAgentId(agent);
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
        LocalDateTime now = utcNow();
        mapper.failExpiredLeases(agentId, now, MAX_DELIVERY_ATTEMPTS, now);
        mapper.requeueExpiredLeases(agentId, now, MAX_DELIVERY_ATTEMPTS, now);
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
        if (mapper.markTerminal(commandId, agentId, result.leaseToken, result.state, now,
                result.code, result.message, result.json) == 1) {
            ProcessingAgentCommandRecord completed = new ProcessingAgentCommandRecord();
            completed.setCommandId(commandId);
            completed.setAgentId(agentId);
            completed.setState(result.state);
            completed.setCompletedAt(now);
            completed.setResultCode(result.code);
            completed.setResultMessage(result.message);
            completed.setResultJson(result.json);
            return toView(completed);
        }
        ProcessingAgentCommandRecord current = mapper.selectById(commandId);
        if (sameLease(current, agentId, result.leaseToken) && isTerminal(current.getState())
                && Objects.equals(current.getState(), result.state)
                && Objects.equals(current.getResultCode(), result.code)
                && Objects.equals(current.getResultMessage(), result.message)
                && Objects.equals(current.getResultJson(), result.json)) {
            return toView(current);
        }
        throw leaseConflict();
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
        return new ValidatedResult(leaseToken, request.getSuccess() ? "SUCCEEDED" : "FAILED",
                code, message, json);
    }

    private String requireLeaseToken(String leaseToken) {
        try {
            return UUID.fromString(leaseToken).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Command lease token is invalid");
        }
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
        private final String state;
        private final String code;
        private final String message;
        private final String json;

        private ValidatedResult(String leaseToken, String state, String code, String message, String json) {
            this.leaseToken = leaseToken;
            this.state = state;
            this.code = code;
            this.message = message;
            this.json = json;
        }
    }
}
