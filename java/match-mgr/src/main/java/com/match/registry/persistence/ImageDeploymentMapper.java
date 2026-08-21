package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImageDeploymentMapper extends BaseMapper<ImageDeploymentRecord> {
    @Select("SELECT * FROM image_deployment ORDER BY requested_at DESC, deployment_id DESC LIMIT #{limit}")
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
}
