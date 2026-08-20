package com.match.licensing.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("license_audit")
public class LicenseAuditRecord {
    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;
    private Integer actorUserId;
    private String action;
    private String result;
    private String reasonCode;
    private String licenseId;
    private String requestId;
    private String correlationId;
    private LocalDateTime createdAt;
}
