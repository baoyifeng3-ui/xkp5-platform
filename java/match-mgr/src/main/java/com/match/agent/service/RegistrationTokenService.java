package com.match.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.agent.model.RegistrationTokenView;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.persistence.RegistrationTokenMapper;
import com.match.agent.persistence.RegistrationTokenRecord;
import com.match.licensing.crypto.Digests;
import com.match.licensing.guard.LicenseGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_VALID_MINUTES = 10;

    private final RegistrationTokenMapper tokenMapper;
    private final ProcessingAgentMapper agentMapper;
    private final LicenseGuard licenseGuard;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RegistrationTokenService(RegistrationTokenMapper tokenMapper,
                                    ProcessingAgentMapper agentMapper,
                                    LicenseGuard licenseGuard,
                                    AgentAuditService auditService,
                                    Clock clock) {
        this.tokenMapper = tokenMapper;
        this.agentMapper = agentMapper;
        this.licenseGuard = licenseGuard;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RegistrationTokenView create(Integer actorUserId, String label) {
        int activeAgents = agentMapper.selectCount(new QueryWrapper<ProcessingAgentRecord>()
                .eq("enabled", true)
                .isNull("removed_at"));
        licenseGuard.requireProcessingServerCapacity(activeAgents + 1);

        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        RegistrationTokenRecord record = new RegistrationTokenRecord();
        record.setTokenId(UUID.randomUUID().toString());
        record.setTokenDigest(Digests.sha256(token));
        record.setLabel(normalizeLabel(label));
        record.setCreatedBy(actorUserId);
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusMinutes(TOKEN_VALID_MINUTES));
        tokenMapper.insert(record);
        auditService.recordSuccess("AGENT_TOKEN_CREATED", actorUserId, null, record.getTokenId());
        return view(record, token);
    }

    public List<RegistrationTokenView> list() {
        List<RegistrationTokenRecord> records = tokenMapper.selectList(
                new QueryWrapper<RegistrationTokenRecord>().orderByDesc("created_at"));
        List<RegistrationTokenView> result = new ArrayList<>();
        for (RegistrationTokenRecord record : records) {
            result.add(view(record, null));
        }
        return result;
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("注册码标签不能超过 80 个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private RegistrationTokenView view(RegistrationTokenRecord record, String plaintextToken) {
        RegistrationTokenView view = new RegistrationTokenView();
        view.setTokenId(record.getTokenId());
        view.setToken(plaintextToken);
        view.setLabel(record.getLabel());
        view.setCreatedBy(record.getCreatedBy());
        view.setCreatedAt(record.getCreatedAt());
        view.setExpiresAt(record.getExpiresAt());
        view.setConsumedAt(record.getConsumedAt());
        view.setRegisteredAgentId(record.getRegisteredAgentId());
        return view;
    }
}
