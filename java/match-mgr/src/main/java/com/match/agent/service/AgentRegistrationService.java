package com.match.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.match.agent.model.AgentRegistrationRequest;
import com.match.agent.model.AgentRegistrationResponse;
import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.persistence.RegistrationTokenMapper;
import com.match.agent.persistence.RegistrationTokenRecord;
import com.match.agent.web.AgentProtocolException;
import com.match.licensing.crypto.Digests;
import com.match.licensing.guard.LicenseGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentRegistrationService {
    private static final Pattern MACHINE_DIGEST = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern MAC_ADDRESS = Pattern.compile("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");

    private final RegistrationTokenMapper tokenMapper;
    private final ProcessingAgentMapper agentMapper;
    private final LicenseGuard licenseGuard;
    private final AgentAuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentRegistrationService(RegistrationTokenMapper tokenMapper,
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
    public AgentRegistrationResponse register(AgentRegistrationRequest request) {
        validateRequest(request);
        String tokenDigest = Digests.sha256(request.getToken());
        RegistrationTokenRecord token = tokenMapper.selectByDigestForUpdate(tokenDigest);
        if (token == null || !MessageDigest.isEqual(
                token.getTokenDigest().getBytes(StandardCharsets.US_ASCII),
                tokenDigest.getBytes(StandardCharsets.US_ASCII)) || token.getConsumedAt() != null) {
            throw new AgentProtocolException("TOKEN_INVALID", "注册码无效", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!now.isBefore(token.getExpiresAt())) {
            throw new AgentProtocolException("TOKEN_EXPIRED", "注册码已过期", HttpStatus.BAD_REQUEST);
        }
        if (agentMapper.selectByMachineDigest(request.getMachineDigest()) != null) {
            throw new AgentProtocolException("MACHINE_ALREADY_REGISTERED", "该处理服务器已经登记",
                    HttpStatus.CONFLICT);
        }
        int activeAgents = agentMapper.selectCount(new QueryWrapper<ProcessingAgentRecord>()
                .eq("enabled", true).isNull("removed_at"));
        licenseGuard.requireProcessingServerCapacity(activeAgents + 1);

        String agentId = UUID.randomUUID().toString();
        String credential = secureCredential();
        ProcessingAgentRecord agent = new ProcessingAgentRecord();
        agent.setAgentId(agentId);
        agent.setDisplayName(request.getDisplayName().trim());
        agent.setMachineDigest(request.getMachineDigest());
        agent.setHostname(request.getHostname().trim());
        agent.setPrimaryIp(request.getPrimaryIp().trim());
        agent.setMacAddress(request.getMacAddress().toLowerCase());
        agent.setAgentVersion(request.getAgentVersion().trim());
        agent.setCredentialDigest(Digests.sha256(credential));
        agent.setEnabled(true);
        agent.setRegisteredAt(now);
        agent.setUpdatedAt(now);
        agentMapper.insert(agent);

        token.setConsumedAt(now);
        token.setRegisteredAgentId(agentId);
        tokenMapper.updateById(token);
        auditService.recordSuccess("AGENT_REGISTERED", null, agentId, token.getTokenId());
        return new AgentRegistrationResponse(agentId, credential);
    }

    private String secureCredential() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateRequest(AgentRegistrationRequest request) {
        if (request == null || blank(request.getToken()) || blank(request.getDisplayName())
                || blank(request.getHostname()) || blank(request.getPrimaryIp())
                || blank(request.getMacAddress()) || blank(request.getAgentVersion())
                || request.getMachineDigest() == null
                || !MACHINE_DIGEST.matcher(request.getMachineDigest()).matches()
                || !MAC_ADDRESS.matcher(request.getMacAddress()).matches()
                || request.getDisplayName().trim().length() > 80
                || request.getHostname().trim().length() > 255
                || request.getPrimaryIp().trim().length() > 45
                || request.getAgentVersion().trim().length() > 40) {
            throw new AgentProtocolException("INVALID_REGISTRATION", "Agent 注册信息无效",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
