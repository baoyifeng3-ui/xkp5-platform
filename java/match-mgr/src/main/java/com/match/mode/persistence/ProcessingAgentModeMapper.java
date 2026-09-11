package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProcessingAgentModeMapper extends BaseMapper<ProcessingAgentModeRecord> {
    @org.apache.ibatis.annotations.Insert("INSERT IGNORE INTO processing_agent_mode "
            + "(agent_id,desired_mode,actual_mode,lock_version,updated_at) VALUES "
            + "(#{agentId},'TRAINING','NORMAL',0,UTC_TIMESTAMP(3))")
    int ensureTraining(@Param("agentId") String agentId);
    @Select("SELECT * FROM processing_agent_mode WHERE agent_id = #{agentId} FOR UPDATE")
    ProcessingAgentModeRecord selectForUpdate(@Param("agentId") String agentId);

    @Update("UPDATE processing_agent_mode SET desired_mode = #{desiredMode}, actual_mode = #{actualMode}, "
            + "active_transition_id = #{transitionId}, updated_at = #{updatedAt}, "
            + "lock_version = lock_version + 1 WHERE agent_id = #{agentId}")
    int updateTransition(@Param("agentId") String agentId, @Param("desiredMode") String desiredMode,
                         @Param("actualMode") String actualMode, @Param("transitionId") String transitionId,
                         @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
