package com.match.dashboard.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_session_activity")
public class UserSessionActivityRecord {
    @TableId(value = "activity_id", type = IdType.AUTO)
    private Long activityId;
    private String sessionDigest;
    private Integer userId;
    private LocalDateTime loginAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expiresAt;
}
