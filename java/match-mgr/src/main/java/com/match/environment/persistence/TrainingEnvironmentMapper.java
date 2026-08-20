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

    @Select("SELECT * FROM training_environment WHERE agent_id = #{agentId} "
            + "ORDER BY environment_id FOR UPDATE")
    List<TrainingEnvironmentRecord> selectByAgentForUpdate(@Param("agentId") String agentId);

    @Select("SELECT * FROM training_environment WHERE user_id = #{userId} "
            + "ORDER BY course_id, environment_id")
    List<TrainingEnvironmentRecord> selectByUser(@Param("userId") int userId);

    @Select("SELECT * FROM training_environment ORDER BY created_at DESC, environment_id DESC")
    List<TrainingEnvironmentRecord> selectAllEnvironments();

    @Select("SELECT * FROM training_environment WHERE desired_state <> 'STOPPED' "
            + "OR actual_state <> 'STOPPED' ORDER BY environment_id")
    List<TrainingEnvironmentRecord> selectRequiringLicenseStop();

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

    @Update("UPDATE training_environment SET actual_state = #{actualState}, "
            + "annotation_container_state = #{annotationState}, editor_container_state = #{editorState}, "
            + "current_operation_id = NULL, updated_at = #{updatedAt}, lock_version = lock_version + 1 "
            + "WHERE environment_id = #{environmentId} AND current_operation_id = #{operationId}")
    int reconcileOperation(@Param("environmentId") String environmentId,
                           @Param("operationId") String operationId,
                           @Param("actualState") String actualState,
                           @Param("annotationState") String annotationState,
                           @Param("editorState") String editorState,
                           @Param("updatedAt") LocalDateTime updatedAt);
}
