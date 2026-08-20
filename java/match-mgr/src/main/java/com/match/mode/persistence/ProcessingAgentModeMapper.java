package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcessingAgentModeMapper extends BaseMapper<ProcessingAgentModeRecord> {
    @Select("SELECT * FROM processing_agent_mode WHERE agent_id = #{agentId} FOR UPDATE")
    ProcessingAgentModeRecord selectForUpdate(@Param("agentId") String agentId);
}
