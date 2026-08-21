package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_upload")
public class ImageUploadRecord {
    @TableId(value = "upload_id", type = IdType.INPUT)
    private String uploadId;
    private String artifactId;
    private String originalFilename;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private Long receivedBytes;
    private String expectedSha256;
    private String finalSha256;
    private String state;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
