package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface TrainingEnvironmentMapper extends BaseMapper<TrainingEnvironmentRecord> {
    @Select("SELECT user_id FROM training_environment WHERE environment_id = #{environmentId}")
    Integer selectUserId(@Param("environmentId") String environmentId);

    @Select("SELECT * FROM training_environment WHERE environment_id = #{environmentId} FOR UPDATE")
    TrainingEnvironmentRecord selectForUpdate(@Param("environmentId") String environmentId);

    @Select("SELECT * FROM training_environment WHERE user_id = #{userId} "
            + "ORDER BY environment_id FOR UPDATE")
    List<TrainingEnvironmentRecord> selectUserEnvironmentsForUpdate(@Param("userId") int userId);

    @Select("SELECT * FROM training_environment WHERE user_id = #{userId} "
            + "ORDER BY course_id, environment_id")
    List<TrainingEnvironmentRecord> selectByUser(@Param("userId") int userId);

    @Update("UPDATE training_environment SET desired_state = #{desiredState}, "
            + "actual_state = #{actualState}, current_operation_id = #{operationId}, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt}, lock_version = lock_version + 1 "
            + "WHERE environment_id = #{environmentId} AND lock_version = #{expectedVersion}")
    int compareAndSetState(@Param("environmentId") String environmentId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("desiredState") String desiredState,
                           @Param("actualState") String actualState,
                           @Param("operationId") String operationId,
                           @Param("updatedBy") int updatedBy,
                           @Param("updatedAt") LocalDateTime updatedAt);
}
