package com.match.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("teams_user")
public class TeamsUser {

    private static final long serialVersionUID = 1L;

    @TableField("teams_id")
    private Integer teamsId;

    @TableField("user_id")
    private Integer userId;
}
