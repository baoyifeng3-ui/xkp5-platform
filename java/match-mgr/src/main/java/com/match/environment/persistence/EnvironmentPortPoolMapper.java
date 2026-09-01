package com.match.environment.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface EnvironmentPortPoolMapper extends BaseMapper<EnvironmentPortPoolRecord> {
    @Select("SELECT * FROM environment_port_pool WHERE agent_id=#{agentId} "
            + "AND environment_type=#{environmentType} AND service_type=#{serviceType} FOR UPDATE")
    EnvironmentPortPoolRecord selectForUpdate(@Param("agentId") String agentId,
                                               @Param("environmentType") String environmentType,
                                               @Param("serviceType") String serviceType);
    @Select("SELECT * FROM environment_port_pool WHERE agent_id=#{agentId} "
            + "ORDER BY environment_type, service_type")
    List<EnvironmentPortPoolRecord> selectByAgent(@Param("agentId") String agentId);
}
