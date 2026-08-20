package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ProcessingEnvironmentSlotMapper extends BaseMapper<ProcessingEnvironmentSlotRecord> {
    @Select("SELECT * FROM processing_environment_slot WHERE agent_id = #{agentId} "
            + "AND slot_number = #{slotNumber} FOR UPDATE")
    ProcessingEnvironmentSlotRecord selectByAgentAndNumberForUpdate(@Param("agentId") String agentId,
                                                                     @Param("slotNumber") int slotNumber);

    @Select("SELECT * FROM processing_environment_slot WHERE agent_id = #{agentId} "
            + "AND user_id = #{userId} FOR UPDATE")
    ProcessingEnvironmentSlotRecord selectByAgentAndUserForUpdate(@Param("agentId") String agentId,
                                                                   @Param("userId") int userId);

    @Update("UPDATE processing_environment_slot SET user_id = #{userId}, updated_at = #{updatedAt} "
            + "WHERE agent_id = #{agentId} AND slot_number = #{slotNumber} AND user_id IS NULL")
    int bindUser(@Param("agentId") String agentId,
                 @Param("slotNumber") int slotNumber,
                 @Param("userId") int userId,
                 @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_environment_slot SET user_id = NULL, updated_at = #{updatedAt} "
            + "WHERE agent_id = #{agentId} AND slot_number = #{slotNumber} AND user_id = #{userId}")
    int unbindUser(@Param("agentId") String agentId,
                   @Param("slotNumber") int slotNumber,
                   @Param("userId") int userId,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
