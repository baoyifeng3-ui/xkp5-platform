package com.match.registry.dto;

import lombok.Data;

@Data
public class ImageUploadChunkView {
    private String uploadId;
    private Integer chunkIndex;
    private Integer chunkSize;
    private String chunkSha256;
    private Long receivedBytes;
    private Integer totalChunks;
    private String state;
}
