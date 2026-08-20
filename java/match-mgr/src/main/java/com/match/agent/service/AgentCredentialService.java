package com.match.agent.service;

import com.match.agent.persistence.ProcessingAgentMapper;
import com.match.agent.persistence.ProcessingAgentRecord;
import com.match.agent.web.AgentProtocolException;
import com.match.licensing.crypto.Digests;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentCredentialService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ProcessingAgentMapper mapper;

    public AgentCredentialService(ProcessingAgentMapper mapper) {
        this.mapper = mapper;
    }

    public ProcessingAgentRecord authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()
                || authorization.substring(BEARER_PREFIX.length()).contains(" ")) {
            throw unauthorized();
        }
        String credential = authorization.substring(BEARER_PREFIX.length());
        ProcessingAgentRecord agent = mapper.selectByCredentialDigest(Digests.sha256(credential));
        if (agent == null) {
            throw unauthorized();
        }
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw new AgentProtocolException("AGENT_DISABLED", "Agent 已停用", HttpStatus.FORBIDDEN);
        }
        return agent;
    }

    private AgentProtocolException unauthorized() {
        return new AgentProtocolException("AGENT_UNAUTHORIZED", "Agent 凭据无效", HttpStatus.UNAUTHORIZED);
    }
}
