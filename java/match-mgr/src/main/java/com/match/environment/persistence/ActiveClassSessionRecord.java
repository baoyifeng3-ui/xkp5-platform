package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("active_class_session")
public class ActiveClassSessionRecord {
    @TableId(value = "session_key", type = IdType.INPUT) private String sessionKey;
    private String environmentId; private String environmentName;
    private String courseId; private String editorTool;
    private Boolean active;
    private Integer startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
}
