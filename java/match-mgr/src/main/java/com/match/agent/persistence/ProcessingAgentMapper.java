package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ProcessingAgentMapper extends BaseMapper<ProcessingAgentRecord> {
    @Select("SELECT * FROM processing_agent WHERE machine_digest = #{machineDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByMachineDigest(@Param("machineDigest") String machineDigest);

    @Select("SELECT * FROM processing_agent WHERE credential_digest = #{credentialDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByCredentialDigest(@Param("credentialDigest") String credentialDigest);

    @Select("SELECT * FROM processing_agent WHERE removed_at IS NULL ORDER BY display_name, agent_id")
    List<ProcessingAgentRecord> selectVisibleAgents();

    @Select("SELECT * FROM processing_agent WHERE agent_id = #{agentId} LIMIT 1")
    ProcessingAgentRecord selectForManagement(@Param("agentId") String agentId);

    @Update("UPDATE processing_agent SET last_seen_at = #{lastSeenAt}, last_boot_id = #{bootId}, "
            + "last_sequence = #{sequence}, agent_version = #{agentVersion}, "
            + "latest_metrics = #{latestMetrics}, updated_at = #{updatedAt} "
            + "WHERE agent_id = #{agentId} AND removed_at IS NULL AND enabled = 1 "
            + "AND (last_boot_id IS NULL OR last_boot_id <> #{bootId} OR last_sequence < #{sequence})")
    int updateLatestIfNew(@Param("agentId") String agentId,
                          @Param("bootId") String bootId,
                          @Param("sequence") long sequence,
                          @Param("lastSeenAt") LocalDateTime lastSeenAt,
                          @Param("agentVersion") String agentVersion,
                          @Param("latestMetrics") String latestMetrics,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_agent SET enabled = #{enabled}, updated_at = #{updatedAt} "
            + "WHERE agent_id = #{agentId} AND removed_at IS NULL")
    int setEnabled(@Param("agentId") String agentId, @Param("enabled") boolean enabled,
                   @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_agent SET enabled = 0, removed_at = #{removedAt}, "
            + "updated_at = #{removedAt} WHERE agent_id = #{agentId} AND removed_at IS NULL")
    int softRemove(@Param("agentId") String agentId, @Param("removedAt") LocalDateTime removedAt);
}
