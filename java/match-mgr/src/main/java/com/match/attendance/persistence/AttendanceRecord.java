package com.match.attendance.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("attendance_record")
public class AttendanceRecord {
    @TableId(type = IdType.AUTO) private Long recordId;
    private String sessionId;
    private Integer userId;
    private LocalDateTime checkedAt;
}
