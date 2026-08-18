package com.match.licensing.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LicenseRequestMapper extends BaseMapper<LicenseRequestRecord> {
    @Select("SELECT * FROM license_request WHERE request_id = #{requestId} FOR UPDATE")
    LicenseRequestRecord selectByIdForUpdate(@Param("requestId") String requestId);
}
