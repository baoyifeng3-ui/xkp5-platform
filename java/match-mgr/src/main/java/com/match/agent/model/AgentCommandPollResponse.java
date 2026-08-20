package com.match.agent.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class AgentCommandPollResponse {
    private final List<AgentCommandEnvelope> commands;

    public AgentCommandPollResponse() {
        this(Collections.emptyList());
    }

    public AgentCommandPollResponse(List<AgentCommandEnvelope> commands) {
        this.commands = commands == null
                ? Collections.emptyList() : Collections.unmodifiableList(commands);
    }
}
