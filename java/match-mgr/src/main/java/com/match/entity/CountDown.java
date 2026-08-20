package com.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.util.Date;

@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("count_down")
public class CountDown {

    private static final long serialVersionUID = 1L;

    @TableId(value = "count_down_id", type = IdType.AUTO)
    private Integer countDownId;

    /**
     * 倒计时
     */
    @TableField(value = "count_down_time", fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private Date countDownTime;

    @TableField("start_time")
    private Date startTime;

    @TableField(value = "scheduled_start_time", updateStrategy = FieldStrategy.IGNORED)
    private Date scheduledStartTime;

    @TableField("pre_login_minutes")
    private Integer preLoginMinutes;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("status")
    private String status;

    @TableField("remaining_seconds")
    private Long remainingSeconds;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField("updated_by")
    private Integer updatedBy;
}
