package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("competition_environment")
public class CompetitionEnvironmentRecord {
    @TableId(value = "environment_id", type = IdType.INPUT)
    private String environmentId;
    private String agentId;
    private String slotId;
    private Integer slotNumber;
    private String annotationTemplateId;
    private Integer annotationTemplateVersion;
    private String editorTemplateId;
    private Integer editorTemplateVersion;
    private String workspaceRelativePath;
    private String desiredState;
    private String actualState;
    private String annotationContainerName;
    private String annotationContainerState;
    private String annotationConfigFingerprint;
    private String editorContainerName;
    private String editorContainerState;
    private String editorConfigFingerprint;
    private LocalDateTime lastVerifiedAt;
    private String lastComponentResultsJson;
    private String currentOperationId;
    private Long lockVersion;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
}
