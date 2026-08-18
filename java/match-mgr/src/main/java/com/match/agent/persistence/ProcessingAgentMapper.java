package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ProcessingAgentMapper extends BaseMapper<ProcessingAgentRecord> {
    @Select("SELECT * FROM processing_agent WHERE machine_digest = #{machineDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByMachineDigest(@Param("machineDigest") String machineDigest);

    @Select("SELECT * FROM processing_agent WHERE credential_digest = #{credentialDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByCredentialDigest(@Param("credentialDigest") String credentialDigest);

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
}
