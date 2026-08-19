package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
