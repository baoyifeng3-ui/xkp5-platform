package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_mode")
public class PlatformModeRecord {
    @TableId(value = "singleton_id", type = IdType.INPUT)
    private Integer singletonId;
    private String mode;
    private Long generation;
    private Integer changedBy;
    private LocalDateTime changedAt;
}
