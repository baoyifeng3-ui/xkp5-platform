package com.match.licensing.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_installation")
public class PlatformInstallation {
    @TableId(value = "installation_key", type = IdType.INPUT)
    private String installationKey;
    private String installationId;
    private LocalDateTime maxTrustedTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
