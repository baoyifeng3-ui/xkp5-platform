package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import com.match.account.model.EligibleAccountView;

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

    @Select("SELECT * FROM processing_environment_slot WHERE user_id=#{userId} "
            + "ORDER BY agent_id, slot_number FOR UPDATE")
    List<ProcessingEnvironmentSlotRecord> selectByUserForUpdate(@Param("userId") int userId);

    @Select("SELECT u.user_id user_id,u.user_name user_name,u.remark remark,"
            + "s.slot_id slot_id,s.slot_number slot_number,a.agent_id agent_id,"
            + "a.display_name agent_name,a.primary_ip primary_ip "
            + "FROM user u JOIN processing_environment_slot s ON s.user_id=u.user_id "
            + "JOIN processing_agent a ON BINARY a.agent_id=BINARY s.agent_id "
            + "LEFT JOIN account_environment_migration m ON m.user_id=u.user_id "
            + "AND m.state NOT IN ('SUCCEEDED','CANCELLED') "
            + "WHERE u.role IN ('USER','ADMIN') AND u.enabled=1 AND a.enabled=1 AND a.removed_at IS NULL "
            + "AND a.last_seen_at >= UTC_TIMESTAMP(3)-INTERVAL 30 SECOND AND m.migration_id IS NULL "
            + "ORDER BY a.display_name,s.slot_number,u.user_name")
    List<EligibleAccountView> selectEligibleAccounts();

    @Select("SELECT u.user_id user_id,u.user_name user_name,u.remark remark,"
            + "s.slot_id slot_id,s.slot_number slot_number,a.agent_id agent_id,"
            + "a.display_name agent_name,a.primary_ip primary_ip "
            + "FROM user u JOIN processing_environment_slot s ON s.user_id=u.user_id "
            + "JOIN processing_agent a ON BINARY a.agent_id=BINARY s.agent_id "
            + "LEFT JOIN account_environment_migration m ON m.user_id=u.user_id "
            + "AND m.state NOT IN ('SUCCEEDED','CANCELLED') "
            + "WHERE u.user_id=#{userId} AND u.role IN ('USER','ADMIN') AND u.enabled=1 AND a.enabled=1 "
            + "AND a.removed_at IS NULL AND a.last_seen_at >= UTC_TIMESTAMP(3)-INTERVAL 30 SECOND "
            + "AND m.migration_id IS NULL LIMIT 1")
    EligibleAccountView selectEligibleAccount(@Param("userId") int userId);

    @Select({"<script>",
            "SELECT * FROM processing_environment_slot WHERE user_id IS NULL ",
            "AND agent_id IN ",
            "<foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            " ORDER BY agent_id,slot_number FOR UPDATE", "</script>"})
    List<ProcessingEnvironmentSlotRecord> selectFreeSlotsForAgentsForUpdate(
            @Param("agentIds") List<String> agentIds);
}
