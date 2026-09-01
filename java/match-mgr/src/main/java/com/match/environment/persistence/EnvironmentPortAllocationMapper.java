package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

public interface EnvironmentPortAllocationMapper extends BaseMapper<EnvironmentPortAllocationRecord> {
    @Select("SELECT * FROM environment_port_allocation WHERE agent_id = #{agentId} "
            + "AND host_port = #{hostPort} AND protocol = #{protocol} FOR UPDATE")
    EnvironmentPortAllocationRecord selectAgentPortForUpdate(@Param("agentId") String agentId,
                                                              @Param("hostPort") int hostPort,
                                                              @Param("protocol") String protocol);

    @Select("SELECT * FROM environment_port_allocation WHERE slot_id = #{slotId} "
            + "ORDER BY component_type, container_port")
    List<EnvironmentPortAllocationRecord> selectBySlot(@Param("slotId") String slotId);

    @Select({"<script>",
            "SELECT * FROM environment_port_allocation WHERE slot_id IN",
            "<foreach collection='slotIds' item='slotId' open='(' separator=',' close=')'>",
            "#{slotId}",
            "</foreach>",
            "ORDER BY slot_id, component_type, container_port",
            "</script>"})
    List<EnvironmentPortAllocationRecord> selectBySlots(@Param("slotIds") List<String> slotIds);

    @Select("SELECT * FROM environment_port_allocation WHERE agent_id=#{agentId} ORDER BY host_port")
    List<EnvironmentPortAllocationRecord> selectByAgent(@Param("agentId") String agentId);

    @Select("SELECT host_port FROM environment_port_allocation WHERE agent_id=#{agentId} "
            + "AND host_port BETWEEN #{rangeStart} AND #{rangeEnd} AND protocol=#{protocol} "
            + "ORDER BY host_port FOR UPDATE")
    List<Integer> selectUsedPortsForUpdate(@Param("agentId") String agentId,
                                           @Param("rangeStart") int rangeStart,
                                           @Param("rangeEnd") int rangeEnd,
                                           @Param("protocol") String protocol);

    @Select("SELECT * FROM environment_port_allocation WHERE environment_id=#{environmentId} "
            + "ORDER BY component_type,container_port")
    List<EnvironmentPortAllocationRecord> selectByEnvironment(@Param("environmentId") String environmentId);

    @Delete("DELETE FROM environment_port_allocation WHERE environment_id=#{environmentId}")
    int deleteByEnvironment(@Param("environmentId") String environmentId);

    @Select("SELECT COUNT(*) FROM environment_port_allocation WHERE agent_id=#{agentId} "
            + "AND host_port BETWEEN #{rangeStart} AND #{rangeEnd}")
    int countInRange(@Param("agentId") String agentId,@Param("rangeStart") int rangeStart,@Param("rangeEnd") int rangeEnd);
}
