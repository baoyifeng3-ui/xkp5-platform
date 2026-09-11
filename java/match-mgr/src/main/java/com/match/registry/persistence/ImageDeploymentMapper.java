package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

public interface ImageDeploymentMapper extends BaseMapper<ImageDeploymentRecord> {
    @Select("SELECT d.*, "
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(c.result_json, '$.percent')) AS UNSIGNED) progress_percent, "
            + "JSON_UNQUOTE(JSON_EXTRACT(c.result_json, '$.stage')) progress_stage, "
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(c.result_json, '$.transferredBytes')) AS UNSIGNED) transferred_bytes, "
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(c.result_json, '$.totalBytes')) AS UNSIGNED) total_bytes "
            + "FROM image_deployment d LEFT JOIN processing_agent_command c ON c.command_id=d.command_id "
            + "ORDER BY d.requested_at DESC, d.deployment_id DESC LIMIT #{limit}")
    java.util.List<ImageDeploymentRecord> selectVisible(@Param("limit") int limit);
    @Select("SELECT * FROM image_deployment WHERE command_id = #{commandId} FOR UPDATE")
    ImageDeploymentRecord selectByCommandIdForUpdate(@Param("commandId") String commandId);

    @Select("SELECT * FROM image_deployment WHERE agent_id = #{agentId} AND component_type = #{componentType} "
            + "AND idempotency_key = #{idempotencyKey} LIMIT 1 FOR UPDATE")
    ImageDeploymentRecord selectByIdempotencyKeyForUpdate(@Param("agentId") String agentId,
                                                           @Param("componentType") String componentType,
                                                           @Param("idempotencyKey") String idempotencyKey);
    @Select("SELECT * FROM image_deployment WHERE agent_id = #{agentId} "
            + "AND component_type = #{componentType} AND active_deployment_key IS NOT NULL "
            + "FOR UPDATE")
    ImageDeploymentRecord selectActiveForUpdate(@Param("agentId") String agentId,
                                                  @Param("componentType") String componentType);

    @Select("SELECT * FROM image_deployment WHERE active_agent_component_key = #{slotKey} FOR UPDATE")
    ImageDeploymentRecord selectActiveSlotForUpdate(@Param("slotKey") String slotKey);

    @Select("SELECT * FROM image_deployment WHERE agent_id = #{agentId} "
            + "AND component_type = #{componentType} AND state = 'SUCCEEDED' "
            + "ORDER BY completed_at DESC, deployment_id DESC LIMIT 1")
    ImageDeploymentRecord selectLatestSucceeded(@Param("agentId") String agentId,
                                                 @Param("componentType") String componentType);
    @Select("SELECT * FROM image_deployment WHERE file_id = #{fileId} AND state = 'SUCCEEDED' "
            + "ORDER BY completed_at DESC, deployment_id DESC LIMIT 1")
    ImageDeploymentRecord selectLatestSucceededByFileId(@Param("fileId") String fileId);

    @Update("UPDATE image_deployment SET active_deployment_key = NULL, "
            + "active_agent_component_key = NULL WHERE deployment_id = #{deploymentId}")
    int clearActiveKeys(@Param("deploymentId") String deploymentId);
    @Delete("DELETE FROM image_deployment WHERE release_id = #{releaseId}")
    int deleteByReleaseId(@Param("releaseId") String releaseId);

    @Select("SELECT * FROM image_deployment WHERE state = 'RUNNING' "
            + "AND requested_at < #{cutoff} ORDER BY requested_at LIMIT #{limit}")
    java.util.List<ImageDeploymentRecord> selectStaleRunning(
            @Param("cutoff") java.time.LocalDateTime cutoff, @Param("limit") int limit);
}
