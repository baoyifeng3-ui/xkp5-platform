package com.match.resource.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("resource_cleanup_audit")
public class ResourceCleanupAuditRecord {
    @TableId(value = "audit_id", type = IdType.INPUT)
    private String auditId;
    private String spaceType;
    private Long deletedFileCount;
    private Long deletedTotalBytes;
    private Integer actorUserId;
    private LocalDateTime createdAt;
}
