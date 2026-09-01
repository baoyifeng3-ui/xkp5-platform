package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("container_template")
public class ContainerTemplateRecord {
    @TableId(value = "template_version_id", type = IdType.INPUT)
    private String templateVersionId;
    private String templateId;
    private Integer templateVersion;
    private String templateName;
    private String componentType;
    private Boolean enabled;
    private String imageReference;
    private String imageDigest;
    private String runtimeName;
    private String restartPolicy;
    private String portsJson;
    private String mountTarget;
    private String commandJson;
    private String workingDirectory;
    private Integer cpuLimitMillis;
    private Long memoryLimitBytes;
    private Boolean gpuEnabled;
    private Boolean mpsEnabled;
    private Integer gpuComputePercent;
    private Long gpuMemoryLimitBytes;
    private String configFingerprint;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime disabledAt;
}
