package com.match.agent.model;

import lombok.Data;

@Data
public class AgentDockerImageView {
    private String repository;
    private String tag;
    private String id;
    private String digest;
    private java.util.List<String> repoTags;
    private Long sizeBytes;
    private Long created;
}
