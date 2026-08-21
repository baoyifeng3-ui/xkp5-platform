package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ProcessingEnvironmentSlotMapper extends BaseMapper<ProcessingEnvironmentSlotRecord> {
    @Select("SELECT * FROM processing_environment_slot WHERE slot_id = #{slotId} FOR UPDATE")
    ProcessingEnvironmentSlotRecord selectForUpdate(@Param("slotId") String slotId);

    @Select("SELECT * FROM processing_environment_slot WHERE agent_id = #{agentId} "
            + "ORDER BY slot_number FOR UPDATE")
    List<ProcessingEnvironmentSlotRecord> selectByAgentForUpdate(@Param("agentId") String agentId);

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

    @Update("UPDATE processing_environment_slot SET user_id = #{userId}, updated_at = #{updatedAt} "
            + "WHERE slot_id = #{slotId} AND user_id IS NULL")
    int bindIfUnbound(@Param("slotId") String slotId, @Param("userId") int userId,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE processing_environment_slot SET user_id = NULL, updated_at = #{updatedAt} "
            + "WHERE slot_id = #{slotId} AND user_id = #{userId}")
    int unbindIfBoundTo(@Param("slotId") String slotId, @Param("userId") int userId,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT * FROM processing_environment_slot ORDER BY agent_id, slot_number")
    List<ProcessingEnvironmentSlotRecord> selectAll();

    @Select("SELECT * FROM processing_environment_slot WHERE user_id = #{userId} "
            + "ORDER BY agent_id, slot_number")
    List<ProcessingEnvironmentSlotRecord> selectByUser(@Param("userId") int userId);
}
