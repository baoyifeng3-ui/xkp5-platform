package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RegistrationTokenMapper extends BaseMapper<RegistrationTokenRecord> {
    @Select("SELECT * FROM processing_agent_registration_token "
            + "WHERE token_digest = #{tokenDigest} FOR UPDATE")
    RegistrationTokenRecord selectByDigestForUpdate(@Param("tokenDigest") String tokenDigest);
}
