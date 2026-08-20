package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ModeTrainingSnapshotMapper extends BaseMapper<ModeTrainingSnapshotRecord> {
    @Select("SELECT * FROM mode_training_snapshot WHERE transition_id = #{transitionId} "
            + "ORDER BY environment_id")
    List<ModeTrainingSnapshotRecord> selectByTransition(@Param("transitionId") String transitionId);
}
