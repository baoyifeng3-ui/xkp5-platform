package com.match.licensing.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("license_request")
public class LicenseRequestRecord {
    @TableId(value = "request_id", type = IdType.INPUT)
    private String requestId;
    private String installationId;
    private String challengeHash;
    private String fingerprintDigest;
    private String environment;
    private String organization;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime consumedAt;
    private String consumedLicenseId;
}
