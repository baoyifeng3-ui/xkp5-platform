package com.match.attendance.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("attendance_session")
public class AttendanceSessionRecord {
    @TableId(type = IdType.INPUT) private String sessionId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
