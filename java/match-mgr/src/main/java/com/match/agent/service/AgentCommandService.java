package com.match.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentCommandEnvelope;
import com.match.agent.model.AgentCommandView;
import com.match.agent.persistence.ProcessingAgentCommandMapper;
import com.match.agent.persistence.ProcessingAgentCommandRecord;
import com.match.agent.persistence.ProcessingAgentRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentCommandService {
    static final String SHUTDOWN_SERVER = "SHUTDOWN_SERVER";
    static final int COMMAND_VERSION = 1;
    static final int MAX_DELIVERY_ATTEMPTS = 5;
    static final long LEASE_SECONDS = 30;

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

    private LocalDateTime utcNow() {
        Instant now = clock.instant();
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
