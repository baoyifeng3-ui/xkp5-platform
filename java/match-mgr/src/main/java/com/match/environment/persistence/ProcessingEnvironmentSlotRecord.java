package com.match.environment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_environment_slot")
public class ProcessingEnvironmentSlotRecord {
    @TableId(value = "slot_id", type = IdType.INPUT)
    private String slotId;
    private String agentId;
    private Integer slotNumber;
    private Integer userId;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
