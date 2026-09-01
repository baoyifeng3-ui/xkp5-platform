package com.match.attendance.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceSessionMapper extends BaseMapper<AttendanceSessionRecord> {
    @Select("SELECT * FROM attendance_session WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1")
    AttendanceSessionRecord selectActive();
    @Update("UPDATE attendance_session SET status='ENDED', ended_at=#{endedAt} WHERE session_id=#{sessionId} AND status='ACTIVE'")
    int end(String sessionId, java.time.LocalDateTime endedAt);
}
