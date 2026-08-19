package com.match.terminal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("processing_agent_terminal_session")
public class TerminalSessionRecord {
    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;
    private String agentId;
    private Integer requesterUserId;
    private String requesterRole;
    private String state;
    private String activeAgentId;
    private String agentTicketDigest;
    private LocalDateTime agentTicketExpiresAt;
    private LocalDateTime agentTicketConsumedAt;
    private String browserTicketDigest;
    private LocalDateTime browserTicketExpiresAt;
    private LocalDateTime browserTicketConsumedAt;
    private String commandId;
    private LocalDateTime requestedAt;
    private LocalDateTime agentConnectedAt;
    private LocalDateTime browserConnectedAt;
    private LocalDateTime activeAt;
    private LocalDateTime lastIoAt;
    private LocalDateTime absoluteExpiresAt;
    private LocalDateTime endedAt;
    private String endReason;
    private String endMessage;
    private Long browserToAgentBytes;
    private Long agentToBrowserBytes;
    private LocalDateTime updatedAt;
}
