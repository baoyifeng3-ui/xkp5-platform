package com.match.agent.service;

import com.match.agent.persistence.AgentAuditMapper;
import com.match.agent.persistence.AgentAuditRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentAuditService {
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");

    private final AgentAuditMapper mapper;
    private final Clock clock;

    public AgentAuditService(AgentAuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void recordSuccess(String action, Integer actorUserId, String agentId, String tokenId) {
        insert(action, "SUCCESS", null, actorUserId, agentId, tokenId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String reasonCode, Integer actorUserId,
                              String agentId, String tokenId) {
        String safeReason = reasonCode != null && SAFE_CODE.matcher(reasonCode).matches()
                ? reasonCode : "UNKNOWN";
        insert(action, "FAILURE", safeReason, actorUserId, agentId, tokenId, null);
    }

    @Transactional
    public void recordCommandSuccess(String action, Integer actorUserId, String agentId,
                                     String commandId) {
        insert(action, "SUCCESS", null, actorUserId, agentId, null, commandId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCommandFailure(String action, String reasonCode, Integer actorUserId,
                                     String agentId, String commandId) {
        String safeReason = reasonCode != null && SAFE_CODE.matcher(reasonCode).matches()
                ? reasonCode : "UNKNOWN";
        insert(action, "FAILURE", safeReason, actorUserId, agentId, null, commandId);
    }

    private void insert(String action, String result, String reasonCode, Integer actorUserId,
                        String agentId, String tokenId, String commandId) {
        if (action == null || !SAFE_CODE.matcher(action).matches()) {
            throw new IllegalArgumentException("Agent 审计操作代码无效");
        }
        AgentAuditRecord record = new AgentAuditRecord();
        record.setActorUserId(actorUserId);
        record.setAgentId(agentId);
        record.setTokenId(tokenId);
        record.setCommandId(commandId);
        record.setAction(action);
        record.setResult(result);
        record.setReasonCode(reasonCode);
        record.setCorrelationId(UUID.randomUUID().toString());
        record.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        mapper.insert(record);
    }
}
