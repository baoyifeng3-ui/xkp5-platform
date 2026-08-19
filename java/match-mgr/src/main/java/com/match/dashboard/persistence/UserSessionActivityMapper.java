package com.match.dashboard.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface UserSessionActivityMapper extends BaseMapper<UserSessionActivityRecord> {
    @Insert("INSERT INTO user_session_activity (session_digest, user_id, login_at, last_activity_at, expires_at) "
            + "VALUES (#{sessionDigest}, #{userId}, #{loginAt}, #{lastActivityAt}, #{expiresAt}) "
            + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), "
            + "last_activity_at = VALUES(last_activity_at), expires_at = VALUES(expires_at)")
    int upsertActivity(@Param("sessionDigest") String sessionDigest, @Param("userId") int userId,
                       @Param("loginAt") LocalDateTime loginAt,
                       @Param("lastActivityAt") LocalDateTime lastActivityAt,
                       @Param("expiresAt") LocalDateTime expiresAt);

    @Delete("DELETE FROM user_session_activity WHERE session_digest = #{sessionDigest}")
    int deleteSession(@Param("sessionDigest") String sessionDigest);

    @Select("SELECT COUNT(DISTINCT user_id) FROM user_session_activity "
            + "WHERE last_activity_at >= #{cutoff} "
            + "AND expires_at > DATE_ADD(#{cutoff}, INTERVAL 5 MINUTE)")
    int countDistinctActiveUsers(@Param("cutoff") LocalDateTime cutoff);

    @Delete("DELETE FROM user_session_activity WHERE expires_at <= #{cutoff} LIMIT #{limit}")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
