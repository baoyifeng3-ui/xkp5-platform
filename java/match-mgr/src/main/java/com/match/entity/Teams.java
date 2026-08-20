package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("teams")
public class Teams {

    private static final long serialVersionUID = 1L;

    @TableId(value = "teams_id", type = IdType.AUTO)
    private Integer teamsId;


    /**
     * 队伍名
     */
    @TableField("teams_name")
    private String teamsName;

    /**
     * 带队老师
     */
    @TableField("teams_teacher")
    private String teamsTeacher;

    /**
     * 进度
     */
    @TableField("speed_progress")
    private Integer speedProgress;


}
