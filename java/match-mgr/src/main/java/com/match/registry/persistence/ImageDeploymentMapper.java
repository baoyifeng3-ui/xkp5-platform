package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImageDeploymentMapper extends BaseMapper<ImageDeploymentRecord> {
    @Select("SELECT * FROM image_deployment WHERE agent_id = #{agentId} "
            + "AND component_type = #{componentType} AND active_deployment_key IS NOT NULL "
            + "FOR UPDATE")
    ImageDeploymentRecord selectActiveForUpdate(@Param("agentId") String agentId,
                                                  @Param("componentType") String componentType);
}
