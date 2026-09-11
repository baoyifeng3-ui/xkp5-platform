package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ProcessingAgentCommandMapper extends BaseMapper<ProcessingAgentCommandRecord> {
    @Select("SELECT COUNT(*) FROM processing_agent_command WHERE agent_id=#{agentId} AND command_type='DOCKER_INVENTORY_ACTION' "
            + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.action'))='DELETE_IMAGE' AND requested_at >= #{since}")
    int countImageDeletesSince(@Param("agentId") String agentId, @Param("since") LocalDateTime since);
    @Update("UPDATE processing_agent_command SET state='FAILED', active_dedup_key=NULL, completed_at=#{now}, result_code='CANCELLED_BY_ENVIRONMENT_DELETE', result_message='Cancelled because environment was deleted', updated_at=#{now} WHERE agent_id=#{agentId} AND state IN ('PENDING','LEASED','RUNNING') AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.environmentId'))=#{environmentId}")
    int cancelEnvironmentCommands(@Param("agentId") String agentId, @Param("environmentId") String environmentId, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM processing_agent_command "
            + "WHERE BINARY command_id = BINARY #{commandId} "
            + "AND BINARY agent_id = BINARY #{agentId} AND BINARY state = BINARY 'RUNNING' "
            + "AND BINARY lease_token = BINARY #{leaseToken} "
            + "AND BINARY command_type = BINARY 'OPEN_ROOT_TERMINAL' "
            + "AND BINARY JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.sessionId')) "
            + "= BINARY #{sessionId} LIMIT 1 FOR UPDATE")
    ProcessingAgentCommandRecord selectRunningTerminalLeaseForUpdate(
            @Param("commandId") String commandId,
            @Param("agentId") String agentId,
            @Param("leaseToken") String leaseToken,
            @Param("sessionId") String sessionId);

    @Select("SELECT agent_id FROM processing_agent WHERE agent_id = #{agentId} "
            + "AND enabled = 1 AND removed_at IS NULL FOR UPDATE")
    String selectEnabledAgentForUpdate(@Param("agentId") String agentId);

    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "AND command_type = #{commandType} AND active_dedup_key IS NOT NULL LIMIT 1")
    ProcessingAgentCommandRecord selectActive(@Param("agentId") String agentId,
                                               @Param("commandType") String commandType);

    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "AND command_type = #{commandType} AND active_dedup_key = #{dedupKey} LIMIT 1")
    ProcessingAgentCommandRecord selectActiveByDedup(@Param("agentId") String agentId,
                                                     @Param("commandType") String commandType,
                                                     @Param("dedupKey") String dedupKey);

    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "ORDER BY requested_at DESC, command_id DESC LIMIT #{limit}")
    List<ProcessingAgentCommandRecord> selectRecent(@Param("agentId") String agentId,
                                                     @Param("limit") int limit);

    @Select("SELECT * FROM processing_agent_command WHERE agent_id = #{agentId} "
            + "AND state = 'PENDING' AND available_at <= #{now} "
            + "ORDER BY requested_at, command_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    ProcessingAgentCommandRecord selectNextForLease(@Param("agentId") String agentId,
                                                     @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_command SET state = 'LEASED', lease_token = #{leaseToken}, "
            + "lease_expires_at = #{leaseExpiresAt}, delivered_at = #{now}, "
            + "attempt_count = attempt_count + 1, updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND state = 'PENDING' AND available_at <= #{now} "
            + "AND EXISTS (SELECT 1 FROM processing_agent a WHERE BINARY a.agent_id = "
            + "BINARY processing_agent_command.agent_id AND a.enabled = 1 AND a.removed_at IS NULL)")
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

    @Select("SELECT * FROM processing_agent_command WHERE state = 'LEASED' "
            + "AND lease_expires_at <= #{now} ORDER BY lease_expires_at, command_id LIMIT #{limit}")
    List<ProcessingAgentCommandRecord> selectExpiredLeases(@Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    @Select("SELECT c.* FROM processing_agent_command c JOIN image_deployment d ON d.command_id = c.command_id "
            + "WHERE c.command_type = 'DEPLOY_IMAGE' AND c.state IN ('FAILED','SUCCEEDED') "
            + "AND d.state NOT IN ('FAILED','SUCCEEDED') ORDER BY c.completed_at, c.command_id LIMIT #{limit}")
    List<ProcessingAgentCommandRecord> selectTerminalImageCommands(@Param("limit") int limit);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{now}, result_code = 'DELIVERY_ATTEMPTS_EXHAUSTED', "
            + "result_message = 'Agent did not acknowledge command delivery', updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND state = 'LEASED' AND lease_expires_at <= #{now} "
            + "AND attempt_count >= #{maxAttempts}")
    int failExpiredLease(@Param("commandId") String commandId,
                         @Param("now") LocalDateTime now,
                         @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE processing_agent_command SET state = 'PENDING', lease_token = NULL, "
            + "lease_expires_at = NULL, available_at = #{now}, updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND state = 'LEASED' AND lease_expires_at <= #{now} "
            + "AND attempt_count < #{maxAttempts}")
    int requeueExpiredLease(@Param("commandId") String commandId,
                            @Param("now") LocalDateTime now,
                            @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = #{resultCode}, "
            + "result_message = 'Command cancelled because the Agent was disabled or removed', "
            + "updated_at = #{completedAt} WHERE agent_id = #{agentId} "
            + "AND state IN ('PENDING', 'LEASED', 'RUNNING')")
    int cancelActiveForAgent(@Param("agentId") String agentId,
                             @Param("resultCode") String resultCode,
                             @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{now}, result_code = 'OPERATOR_CLOSED', "
            + "result_message = '远程终端已由超级管理员关闭', updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND command_type = 'OPEN_ROOT_TERMINAL' "
            + "AND state IN ('PENDING','LEASED','RUNNING')")
    int cancelTerminalCommand(@Param("commandId") String commandId,
                              @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_command SET state = 'RUNNING', started_at = #{startedAt}, "
            + "updated_at = #{startedAt} WHERE command_id = #{commandId} AND agent_id = #{agentId} "
            + "AND state = 'LEASED' AND lease_token = #{leaseToken} "
            + "AND lease_expires_at > #{startedAt} "
            + "AND EXISTS (SELECT 1 FROM processing_agent a WHERE BINARY a.agent_id = "
            + "BINARY processing_agent_command.agent_id AND a.enabled = 1 AND a.removed_at IS NULL)")
    int markRunning(@Param("commandId") String commandId,
                    @Param("agentId") String agentId,
                    @Param("leaseToken") String leaseToken,
                    @Param("startedAt") LocalDateTime startedAt);

    @Update("UPDATE processing_agent_command SET state = #{terminalState}, active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = #{resultCode}, "
            + "result_message = #{resultMessage}, result_json = #{resultJson}, updated_at = #{completedAt} "
            + "WHERE command_id = #{commandId} AND agent_id = #{agentId} "
            + "AND state = 'RUNNING' AND lease_token = #{leaseToken} "
            + "AND EXISTS (SELECT 1 FROM processing_agent a WHERE BINARY a.agent_id = "
            + "BINARY processing_agent_command.agent_id AND a.enabled = 1 AND a.removed_at IS NULL)")
    int markTerminal(@Param("commandId") String commandId,
                     @Param("agentId") String agentId,
                     @Param("leaseToken") String leaseToken,
                     @Param("terminalState") String terminalState,
                     @Param("completedAt") LocalDateTime completedAt,
                     @Param("resultCode") String resultCode,
                     @Param("resultMessage") String resultMessage,
                     @Param("resultJson") String resultJson);

    @Update("UPDATE processing_agent_command SET result_code = #{resultCode}, result_message = #{resultMessage}, "
            + "result_json = #{resultJson}, updated_at = #{updatedAt} WHERE command_id = #{commandId} "
            + "AND agent_id = #{agentId} AND state = 'RUNNING' AND lease_token = #{leaseToken} "
            + "AND command_type = 'DEPLOY_IMAGE'")
    int markImageProgress(@Param("commandId") String commandId, @Param("agentId") String agentId,
                          @Param("leaseToken") String leaseToken, @Param("resultCode") String resultCode,
                          @Param("resultMessage") String resultMessage, @Param("resultJson") String resultJson,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{now}, result_code = 'IMAGE_DEPLOYMENT_TIMEOUT', "
            + "result_message = '镜像推送超过时限，已释放服务器命令队列', updated_at = #{now} "
            + "WHERE command_id = #{commandId} AND command_type = 'DEPLOY_IMAGE' "
            + "AND state = 'RUNNING' AND updated_at < #{cutoff}")
    int failStaleImageDeployment(@Param("commandId") String commandId,
                                 @Param("cutoff") LocalDateTime cutoff,
                                 @Param("now") LocalDateTime now);

    @Update("UPDATE processing_agent_command SET result_code = #{resultCode}, "
            + "result_message = #{resultMessage}, result_json = #{resultJson}, updated_at = #{updatedAt} "
            + "WHERE command_id = #{commandId} AND agent_id = #{agentId} "
            + "AND state = 'RUNNING' AND lease_token = #{leaseToken} "
            + "AND command_type = 'SHUTDOWN_SERVER' "
            + "AND EXISTS (SELECT 1 FROM processing_agent a WHERE BINARY a.agent_id = "
            + "BINARY processing_agent_command.agent_id AND a.enabled = 1 AND a.removed_at IS NULL)")
    int recordShutdownAccepted(@Param("commandId") String commandId,
                               @Param("agentId") String agentId,
                               @Param("leaseToken") String leaseToken,
                               @Param("updatedAt") LocalDateTime updatedAt,
                               @Param("resultCode") String resultCode,
                               @Param("resultMessage") String resultMessage,
                               @Param("resultJson") String resultJson);

    @Select("SELECT c.command_id, c.agent_id, c.started_at, a.last_seen_at "
            + "FROM processing_agent_command c "
            + "LEFT JOIN processing_agent a ON BINARY a.agent_id = BINARY c.agent_id "
            + "WHERE c.command_type = 'SHUTDOWN_SERVER' AND c.state = 'RUNNING' "
            + "ORDER BY c.started_at, c.command_id LIMIT #{limit}")
    List<RunningShutdownCandidate> selectRunningShutdowns(@Param("limit") int limit);

    @Update("UPDATE processing_agent_command SET state = 'SUCCEEDED', active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = 'OFFLINE_CONFIRMED', "
            + "result_message = 'Agent heartbeat stopped after shutdown started', updated_at = #{completedAt} "
            + "WHERE command_id = #{commandId} AND state = 'RUNNING' "
            + "AND command_type = 'SHUTDOWN_SERVER'")
    int confirmShutdownOffline(@Param("commandId") String commandId,
                               @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE processing_agent_command SET state = 'FAILED', active_dedup_key = NULL, "
            + "completed_at = #{completedAt}, result_code = 'SHUTDOWN_NOT_CONFIRMED', "
            + "result_message = 'Agent remained online after shutdown confirmation deadline', "
            + "updated_at = #{completedAt} WHERE command_id = #{commandId} AND state = 'RUNNING' "
            + "AND command_type = 'SHUTDOWN_SERVER'")
    int failShutdownConfirmation(@Param("commandId") String commandId,
                                 @Param("completedAt") LocalDateTime completedAt);
}
