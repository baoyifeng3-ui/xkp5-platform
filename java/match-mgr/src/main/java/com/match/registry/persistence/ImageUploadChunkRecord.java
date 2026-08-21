package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_upload_chunk")
public class ImageUploadChunkRecord {
    private String uploadId;
    private Integer chunkIndex;
    private Integer chunkSize;
    private String chunkSha256;
    private Long storedBytes;
    private LocalDateTime createdAt;
}
