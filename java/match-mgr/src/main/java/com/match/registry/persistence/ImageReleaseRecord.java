package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_release")
public class ImageReleaseRecord {
    @TableId(value = "release_id", type = IdType.INPUT)
    private String releaseId;
    private String groupId;
    private String componentType;
    private String artifactId;
    private String registryDigest;
    private String version;
    private String state;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
