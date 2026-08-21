package com.match.mode.service;

import com.match.mode.persistence.ProcessingAgentModeMapper;
import com.match.mode.persistence.ProcessingAgentModeRecord;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProcessingAgentModeGuard {
    private final ProcessingAgentModeMapper mapper;

    public ProcessingAgentModeGuard(ProcessingAgentModeMapper mapper) {
        this.mapper = mapper;
    }

    public void requireIdleForBinding(String agentId) {
        ProcessingAgentModeRecord mode = mapper.selectForUpdate(agentId);
        if (mode == null || !Objects.equals(agentId, mode.getAgentId())) {
            throw new ModeConflictException("AGENT_MODE_NOT_READY",
                    "Processing Agent mode is not initialized");
        }
        if (mode.getActiveTransitionId() != null) {
            throw new ModeConflictException("AGENT_MODE_TRANSITION_ACTIVE",
                    "Processing Agent has an active mode transition");
        }
        if (!"TRAINING".equals(mode.getDesiredMode()) || !"NORMAL".equals(mode.getActualMode())) {
            throw new ModeConflictException("AGENT_MODE_NOT_IDLE",
                    "Processing Agent is not idle in training mode");
        }
    }
}
