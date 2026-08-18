package com.match.agent.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class AgentCommandPollResponse {
    private final List<Object> commands = Collections.emptyList();
}
