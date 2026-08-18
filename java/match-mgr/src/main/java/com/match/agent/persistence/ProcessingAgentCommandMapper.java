package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ProcessingAgentCommandMapper extends BaseMapper<ProcessingAgentCommandRecord> {
    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "AND command_type = #{commandType} AND active_dedup_key IS NOT NULL LIMIT 1")
    ProcessingAgentCommandRecord selectActive(@Param("agentId") String agentId,
                                               @Param("commandType") String commandType);

    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "AND state = 'PENDING' AND available_at <= #{now} "
            + "ORDER BY requested_at, command_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    ProcessingAgentCommandRecord selectNextForLease(@Param("agentId") String agentId,
                                                     @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_command SET state = 'LEASED', lease_token = #{leaseToken}, "
            + "lease_expires_at = #{leaseExpiresAt}, delivered_at = #{now}, "
            + "attempt_count = attempt_count + 1, updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND state = 'PENDING' AND available_at <= #{now}")
    int markLeased(@Param("commandId") String commandId,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = 'DELIVERY_ATTEMPTS_EXHAUSTED', "
            + "result_message = 'Agent did not acknowledge command delivery', updated_at = #{completedAt} "
            + "WHERE agent_id = #{agentId} AND state = 'LEASED' AND lease_expires_at <= #{now} "
            + "AND attempt_count >= #{maxAttempts}")
    int failExpiredLeases(@Param("agentId") String agentId,
                          @Param("now") LocalDateTime now,
                          @Param("maxAttempts") int maxAttempts,
                          @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE processing_agent_command SET state = 'PENDING', lease_token = NULL, "
            + "lease_expires_at = NULL, available_at = #{now}, updated_at = #{updatedAt} "
            + "WHERE agent_id = #{agentId} AND state = 'LEASED' AND lease_expires_at <= #{now} "
            + "AND attempt_count < #{maxAttempts}")
    int requeueExpiredLeases(@Param("agentId") String agentId,
                             @Param("now") LocalDateTime now,
                             @Param("maxAttempts") int maxAttempts,
                             @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_agent_command SET state = 'RUNNING', started_at = #{startedAt}, "
            + "updated_at = #{startedAt} WHERE command_id = #{commandId} AND agent_id = #{agentId} "
            + "AND state = 'LEASED' AND lease_token = #{leaseToken}")
    int markRunning(@Param("commandId") String commandId,
                    @Param("agentId") String agentId,
                    @Param("leaseToken") String leaseToken,
                    @Param("startedAt") LocalDateTime startedAt);

    @Update("UPDATE processing_agent_command SET state = #{terminalState}, active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = #{resultCode}, "
            + "result_message = #{resultMessage}, result_json = #{resultJson}, updated_at = #{completedAt} "
            + "WHERE command_id = #{commandId} AND agent_id = #{agentId} "
            + "AND state = 'RUNNING' AND lease_token = #{leaseToken}")
    int markTerminal(@Param("commandId") String commandId,
                     @Param("agentId") String agentId,
                     @Param("leaseToken") String leaseToken,
                     @Param("terminalState") String terminalState,
                     @Param("completedAt") LocalDateTime completedAt,
                     @Param("resultCode") String resultCode,
                     @Param("resultMessage") String resultMessage,
                     @Param("resultJson") String resultJson);
}
