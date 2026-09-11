package com.match.registry.dto;

import lombok.Data;

@Data
public class ImageUploadCreateRequest {
    private String artifactId;
    private String groupId;
    private String componentType;
    private String originalFilename;
    private Long totalSize;
    private Integer chunkSize;
    private String expectedSha256;
    private String version;
    private String imageRepository;
    private String imageTag;
    private Boolean overwrite;
}
