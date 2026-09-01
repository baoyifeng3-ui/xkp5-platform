package com.match.attendance.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
    @Select("SELECT user_id FROM attendance_record WHERE session_id=#{sessionId}")
    List<Integer> selectUserIds(String sessionId);
    @Select("SELECT * FROM attendance_record WHERE session_id=#{sessionId} AND user_id=#{userId} LIMIT 1")
    AttendanceRecord selectByUser(String sessionId, Integer userId);
}
