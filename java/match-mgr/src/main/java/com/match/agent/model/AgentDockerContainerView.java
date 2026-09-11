package com.match.agent.model;

import lombok.Data;

@Data
public class AgentDockerContainerView {
    private String name;
    private String id;
    private String image;
    private String state;
    private String status;
    private Long created;
}
