package com.match.resource.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("platform_file")
public class PlatformFileRecord {
    @TableId(value = "file_id", type = IdType.INPUT)
    private String fileId;
    private String spaceType;
    private Integer ownerUserId;
    private String directoryId;
    private String fileName;
    private String storageKey;
    private Long contentLength;
    private String sha256;
    private String mimeType;
    private Integer uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
