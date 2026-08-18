package com.match.licensing.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface PlatformInstallationMapper extends BaseMapper<PlatformInstallation> {
    @Update("UPDATE platform_installation "
            + "SET max_trusted_time = #{trustedTime}, updated_at = #{updatedAt} "
            + "WHERE installation_key = 'PRIMARY' "
            + "AND (max_trusted_time IS NULL OR max_trusted_time < #{trustedTime})")
    int advanceMaxTrustedTime(@Param("trustedTime") LocalDateTime trustedTime,
                              @Param("updatedAt") LocalDateTime updatedAt);
}
