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

    @Update("UPDATE mode_transition_step SET state = 'DISPATCHED', command_id = #{commandId}, "
            + "updated_at = #{updatedAt} WHERE step_id = #{stepId} AND state = 'PENDING'")
    int markDispatched(@Param("stepId") String stepId, @Param("commandId") String commandId,
                       @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
