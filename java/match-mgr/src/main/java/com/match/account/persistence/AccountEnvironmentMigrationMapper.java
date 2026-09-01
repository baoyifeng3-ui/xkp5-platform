package com.match.account.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AccountEnvironmentMigrationMapper extends BaseMapper<AccountEnvironmentMigrationRecord> {
    @Select("SELECT * FROM account_environment_migration WHERE user_id=#{userId} "
            + "AND state NOT IN ('SUCCEEDED','CANCELLED') ORDER BY requested_at DESC LIMIT 1")
    AccountEnvironmentMigrationRecord selectActiveByUser(@Param("userId") Integer userId);
}
