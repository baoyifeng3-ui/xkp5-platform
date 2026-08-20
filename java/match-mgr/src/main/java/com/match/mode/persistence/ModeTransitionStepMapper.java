package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ModeTransitionStepMapper extends BaseMapper<ModeTransitionStepRecord> {
    @Select("SELECT * FROM mode_transition_step WHERE transition_id = #{transitionId} "
            + "ORDER BY phase_number, step_ordinal")
    List<ModeTransitionStepRecord> selectByTransition(@Param("transitionId") String transitionId);

    @Select("SELECT * FROM mode_transition_step WHERE transition_id = #{transitionId} "
            + "AND phase_number = #{phaseNumber} ORDER BY step_ordinal")
    List<ModeTransitionStepRecord> selectByPhase(@Param("transitionId") String transitionId,
                                                  @Param("phaseNumber") int phaseNumber);

    @Select("SELECT * FROM mode_transition_step WHERE command_id = #{commandId} FOR UPDATE")
    ModeTransitionStepRecord selectByCommandForUpdate(@Param("commandId") String commandId);

    @Select("SELECT * FROM mode_transition_step WHERE step_id = #{stepId} FOR UPDATE")
    ModeTransitionStepRecord selectForUpdate(@Param("stepId") String stepId);

    @Update("UPDATE mode_transition_step SET state = 'DISPATCHED', command_id = #{commandId}, "
            + "updated_at = #{updatedAt} WHERE step_id = #{stepId} AND state = 'PENDING'")
    int markDispatched(@Param("stepId") String stepId, @Param("commandId") String commandId,
                       @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Update("UPDATE mode_transition_step SET state = #{state}, result_code = #{resultCode}, "
            + "result_message = #{resultMessage}, component_results_json = #{componentResultsJson}, "
            + "updated_at = #{updatedAt} WHERE step_id = #{stepId} "
            + "AND state NOT IN ('SUCCEEDED','FAILED')")
    int markTerminal(@Param("stepId") String stepId, @Param("state") String state,
                     @Param("resultCode") String resultCode,
                     @Param("resultMessage") String resultMessage,
                     @Param("componentResultsJson") String componentResultsJson,
                     @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Update("UPDATE mode_transition_step SET state = 'PENDING', command_id = NULL, "
            + "result_code = NULL, result_message = NULL, component_results_json = NULL, "
            + "updated_at = #{updatedAt} WHERE transition_id = #{transitionId} "
            + "AND phase_number = #{phaseNumber} AND state = 'FAILED'")
    int resetFailedInPhase(@Param("transitionId") String transitionId,
                           @Param("phaseNumber") int phaseNumber,
                           @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
