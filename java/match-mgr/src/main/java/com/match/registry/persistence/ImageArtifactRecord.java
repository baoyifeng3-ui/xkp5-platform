package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_artifact")
public class ImageArtifactRecord {
    @TableId(value = "artifact_id", type = IdType.INPUT)
    private String artifactId;
    private String groupId;
    private String componentType;
    private String originalFilename;
    private Long sizeBytes;
    private String sha256;
    private String imageRepository;
    private String imageTag;
    private String version;
    private String registryDigest;
    private String reviewState;
    private String importState;
    private String failureCode;
    private String failureMessage;
    private Integer reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime importedAt;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
