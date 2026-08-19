package com.match.terminal.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface TerminalSessionMapper {
    @Select("SELECT * FROM processing_agent_terminal_session "
            + "WHERE session_id = #{sessionId} LIMIT 1")
    TerminalSessionRecord selectById(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM processing_agent_terminal_session "
            + "WHERE active_agent_id = #{agentId} LIMIT 1")
    TerminalSessionRecord selectActiveByAgent(@Param("agentId") String agentId);

    @Select("SELECT * FROM processing_agent_terminal_session "
            + "WHERE command_id = #{commandId} LIMIT 1")
    TerminalSessionRecord selectByCommandId(@Param("commandId") String commandId);

    @Insert("INSERT INTO processing_agent_terminal_session (session_id, agent_id, requester_user_id, "
            + "requester_role, state, active_agent_id, agent_ticket_digest, agent_ticket_expires_at, "
            + "agent_ticket_consumed_at, browser_ticket_digest, browser_ticket_expires_at, "
            + "browser_ticket_consumed_at, command_id, requested_at, agent_connected_at, "
            + "browser_connected_at, active_at, last_io_at, absolute_expires_at, ended_at, end_reason, "
            + "end_message, updated_at) VALUES "
            + "(#{sessionId}, #{agentId}, #{requesterUserId}, #{requesterRole}, #{state}, "
            + "#{activeAgentId}, #{agentTicketDigest}, #{agentTicketExpiresAt}, #{agentTicketConsumedAt}, "
            + "#{browserTicketDigest}, #{browserTicketExpiresAt}, #{browserTicketConsumedAt}, #{commandId}, "
            + "#{requestedAt}, #{agentConnectedAt}, #{browserConnectedAt}, #{activeAt}, #{lastIoAt}, "
            + "#{absoluteExpiresAt}, #{endedAt}, #{endReason}, #{endMessage}, #{updatedAt})")
    int insert(TerminalSessionRecord record);

    @Update("UPDATE processing_agent_terminal_session SET command_id = #{commandId}, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} AND state = 'WAITING_AGENT' AND command_id IS NULL "
            + "AND absolute_expires_at > #{now}")
    int setCommand(@Param("sessionId") String sessionId,
                   @Param("commandId") String commandId,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET agent_ticket_digest = #{digest}, "
            + "agent_ticket_expires_at = #{expiresAt}, agent_ticket_consumed_at = NULL, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} AND state = 'WAITING_AGENT' "
            + "AND absolute_expires_at > #{now} "
            + "AND DATE_ADD(requested_at, INTERVAL 90 SECOND) > #{now} AND #{expiresAt} > #{now} "
            + "AND #{expiresAt} <= LEAST(DATE_ADD(requested_at, INTERVAL 90 SECOND), absolute_expires_at)")
    int issueAgentTicket(@Param("sessionId") String sessionId,
                         @Param("digest") String digest,
                         @Param("expiresAt") LocalDateTime expiresAt,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET state = 'WAITING_BROWSER', "
            + "agent_ticket_consumed_at = #{now}, agent_connected_at = #{now}, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} AND state = 'WAITING_AGENT' "
            + "AND agent_ticket_digest = #{digest} AND agent_ticket_consumed_at IS NULL "
            + "AND agent_ticket_expires_at > #{now} AND absolute_expires_at > #{now} "
            + "AND DATE_ADD(requested_at, INTERVAL 90 SECOND) > #{now}")
    int consumeAgentTicket(@Param("sessionId") String sessionId,
                           @Param("digest") String digest,
                           @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET browser_ticket_digest = #{digest}, "
            + "browser_ticket_expires_at = #{expiresAt}, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} AND state = 'WAITING_BROWSER' "
            + "AND browser_ticket_consumed_at IS NULL AND browser_connected_at IS NULL "
            + "AND absolute_expires_at > #{now} "
            + "AND DATE_ADD(agent_connected_at, INTERVAL 60 SECOND) > #{now} AND #{expiresAt} > #{now} "
            + "AND #{expiresAt} <= LEAST(DATE_ADD(agent_connected_at, INTERVAL 60 SECOND), "
            + "absolute_expires_at)")
    int issueBrowserTicket(@Param("sessionId") String sessionId,
                           @Param("digest") String digest,
                           @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET browser_ticket_consumed_at = #{now}, "
            + "browser_connected_at = #{now}, updated_at = #{now} WHERE session_id = #{sessionId} "
            + "AND state = 'WAITING_BROWSER' AND browser_ticket_digest = #{digest} "
            + "AND browser_ticket_consumed_at IS NULL AND browser_ticket_expires_at > #{now} "
            + "AND absolute_expires_at > #{now} "
            + "AND DATE_ADD(agent_connected_at, INTERVAL 60 SECOND) > #{now}")
    int consumeBrowserTicket(@Param("sessionId") String sessionId,
                             @Param("digest") String digest,
                             @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET state = 'ACTIVE', active_at = #{now}, "
            + "last_io_at = #{now}, updated_at = #{now} WHERE session_id = #{sessionId} "
            + "AND state = 'WAITING_BROWSER' "
            + "AND agent_connected_at IS NOT NULL AND browser_connected_at IS NOT NULL "
            + "AND agent_ticket_consumed_at IS NOT NULL AND browser_ticket_consumed_at IS NOT NULL "
            + "AND absolute_expires_at > #{now}")
    int markActive(@Param("sessionId") String sessionId,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session "
            + "SET browser_to_agent_bytes = browser_to_agent_bytes + #{browserToAgent}, "
            + "agent_to_browser_bytes = agent_to_browser_bytes + #{agentToBrowser}, "
            + "last_io_at = GREATEST(COALESCE(last_io_at, active_at, #{ioAt}), #{ioAt}), "
            + "updated_at = GREATEST(updated_at, #{ioAt}) WHERE session_id = #{sessionId} "
            + "AND state = 'ACTIVE' AND absolute_expires_at > #{ioAt} "
            + "AND #{browserToAgent} >= 0 AND #{agentToBrowser} >= 0")
    int addTraffic(@Param("sessionId") String sessionId,
                   @Param("browserToAgent") long browserToAgent,
                   @Param("agentToBrowser") long agentToBrowser,
                   @Param("ioAt") LocalDateTime ioAt);

    @Update("UPDATE processing_agent_terminal_session SET state = #{state}, active_agent_id = NULL, "
            + "agent_ticket_digest = NULL, browser_ticket_digest = NULL, ended_at = #{now}, "
            + "end_reason = #{reason}, end_message = #{message}, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} "
            + "AND state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE') "
            + "AND #{state} IN ('CLOSED', 'FAILED')")
    int close(@Param("sessionId") String sessionId,
              @Param("state") String state,
              @Param("reason") String reason,
              @Param("message") String message,
              @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_terminal_session SET state = #{state}, active_agent_id = NULL, "
            + "agent_ticket_digest = NULL, browser_ticket_digest = NULL, ended_at = #{now}, "
            + "end_reason = #{reason}, end_message = #{message}, updated_at = #{now} "
            + "WHERE session_id = #{sessionId} AND #{state} IN ('CLOSED', 'FAILED') "
            + "AND state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE') "
            + "AND (absolute_expires_at <= #{now} "
            + "OR (state = 'WAITING_AGENT' AND requested_at <= DATE_SUB(#{now}, INTERVAL 90 SECOND)) "
            + "OR (state = 'WAITING_BROWSER' AND agent_connected_at <= DATE_SUB(#{now}, INTERVAL 60 SECOND)) "
            + "OR (state = 'ACTIVE' AND COALESCE(last_io_at, active_at) <= #{idleBefore}))")
    int closeExpired(@Param("sessionId") String sessionId,
                     @Param("idleBefore") LocalDateTime idleBefore,
                     @Param("now") LocalDateTime now,
                     @Param("state") String state,
                     @Param("reason") String reason,
                     @Param("message") String message);

    @Select("SELECT * FROM processing_agent_terminal_session "
            + "WHERE state IN ('WAITING_AGENT', 'WAITING_BROWSER', 'ACTIVE') "
            + "AND (absolute_expires_at <= #{now} "
            + "OR (state = 'WAITING_AGENT' AND requested_at <= DATE_SUB(#{now}, INTERVAL 90 SECOND)) "
            + "OR (state = 'WAITING_BROWSER' AND agent_connected_at <= DATE_SUB(#{now}, INTERVAL 60 SECOND)) "
            + "OR (state = 'ACTIVE' AND COALESCE(last_io_at, active_at) <= #{idleBefore})) "
            + "ORDER BY absolute_expires_at, requested_at, session_id LIMIT #{limit}")
    List<TerminalSessionRecord> selectExpired(@Param("idleBefore") LocalDateTime idleBefore,
                                               @Param("now") LocalDateTime now,
                                               @Param("limit") int limit);
}
