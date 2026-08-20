package com.match.mode.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent_mode")
public class ProcessingAgentModeRecord {
    @TableId(value = "agent_id", type = IdType.INPUT)
    private String agentId;
    private String desiredMode;
    private String actualMode;
    private String activeTransitionId;
    private Long lockVersion;
    private LocalDateTime updatedAt;
}
