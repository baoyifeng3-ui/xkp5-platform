package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_file")
public class ImageFileRecord {
    @TableId(value = "file_id", type = IdType.INPUT)
    private String fileId;
    private String imageRepository;
    private String originalFilename;
    private String imageTag;
    private String archivePath;
    private Long sizeBytes;
    private Boolean enabled;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
