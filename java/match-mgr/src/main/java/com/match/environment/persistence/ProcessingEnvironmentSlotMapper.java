package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcessingEnvironmentSlotMapper extends BaseMapper<ProcessingEnvironmentSlotRecord> {
    @Select("SELECT * FROM processing_environment_slot WHERE agent_id = #{agentId} "
            + "AND slot_number = #{slotNumber} FOR UPDATE")
    ProcessingEnvironmentSlotRecord selectByAgentAndNumberForUpdate(@Param("agentId") String agentId,
                                                                     @Param("slotNumber") int slotNumber);

    @Select("SELECT * FROM processing_environment_slot WHERE agent_id = #{agentId} "
            + "AND user_id = #{userId} FOR UPDATE")
    ProcessingEnvironmentSlotRecord selectByAgentAndUserForUpdate(@Param("agentId") String agentId,
                                                                   @Param("userId") int userId);
}
