package com.match.licensing.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_license")
public class PlatformLicenseRecord {
    @TableId(value = "license_id", type = IdType.INPUT)
    private String licenseId;
    private Boolean active;
    private String requestId;
    private String organization;
    private String environment;
    private String fingerprintDigest;
    private String keyId;
    private LocalDateTime notBefore;
    private LocalDateTime expiresAt;
    private Integer maxProcessingServers;
    private String signedEnvelope;
    private Integer importedBy;
    private LocalDateTime importedAt;
}
