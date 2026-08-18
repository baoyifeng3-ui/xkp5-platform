package com.match.licensing.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PlatformLicenseMapper extends BaseMapper<PlatformLicenseRecord> {
    @Select("SELECT * FROM platform_license WHERE active = 1 LIMIT 1")
    PlatformLicenseRecord selectActive();

    @Update("UPDATE platform_license SET active = 0 WHERE active = 1")
    int deactivateAll();
}
