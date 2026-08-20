package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ModeTransitionMapper extends BaseMapper<ModeTransitionRecord> {
    @Select("SELECT * FROM mode_transition WHERE transition_id = #{transitionId} FOR UPDATE")
    ModeTransitionRecord selectForUpdate(@Param("transitionId") String transitionId);

    @Select("SELECT * FROM mode_transition WHERE agent_id = #{agentId} "
            + "AND state IN ('PENDING','RUNNING','DEGRADED') ORDER BY requested_at DESC LIMIT 1")
    ModeTransitionRecord selectActiveForUpdate(@Param("agentId") String agentId);

    @Select("SELECT * FROM mode_transition WHERE agent_id = #{agentId} "
            + "AND target_mode = 'COMPETITION' ORDER BY requested_at DESC LIMIT 1")
    ModeTransitionRecord selectLatestCompetitionForAgent(@Param("agentId") String agentId);

    @Select("SELECT * FROM mode_transition WHERE state IN ('PENDING','RUNNING','DEGRADED') "
            + "ORDER BY requested_at, transition_id LIMIT #{limit}")
    List<ModeTransitionRecord> selectActiveForRecovery(@Param("limit") int limit);

    @Select("SELECT * FROM mode_transition ORDER BY requested_at DESC, transition_id DESC LIMIT #{limit}")
    List<ModeTransitionRecord> selectRecent(@Param("limit") int limit);

    @Update("UPDATE mode_transition SET state = #{state}, failure_summary = #{failureSummary}, "
            + "completed_at = #{completedAt}, updated_at = #{completedAt}, active_transition_key = "
            + "CASE WHEN #{state} IN ('SUCCEEDED') THEN NULL ELSE active_transition_key END "
            + "WHERE transition_id = #{transitionId}")
    int updateTerminal(@Param("transitionId") String transitionId,
                       @Param("state") String state,
                       @Param("failureSummary") String failureSummary,
                       @Param("completedAt") java.time.LocalDateTime completedAt);

    @Update("UPDATE mode_transition SET state = #{state}, failure_summary = #{failureSummary}, "
            + "updated_at = #{updatedAt} WHERE transition_id = #{transitionId}")
    int updateState(@Param("transitionId") String transitionId,
                    @Param("state") String state,
                    @Param("failureSummary") String failureSummary,
                    @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
