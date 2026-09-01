package com.match.resource.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("resource_directory")
public class ResourceDirectoryRecord {
    @TableId(value = "directory_id", type = IdType.INPUT)
    private String directoryId;
    private String spaceType;
    private Integer ownerUserId;
    private String parentId;
    private String name;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
