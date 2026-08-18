package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcessingAgentMapper extends BaseMapper<ProcessingAgentRecord> {
    @Select("SELECT * FROM processing_agent WHERE machine_digest = #{machineDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByMachineDigest(@Param("machineDigest") String machineDigest);

    @Select("SELECT * FROM processing_agent WHERE credential_digest = #{credentialDigest} "
            + "AND removed_at IS NULL LIMIT 1")
    ProcessingAgentRecord selectByCredentialDigest(@Param("credentialDigest") String credentialDigest);
}
