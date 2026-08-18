package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("announcement_field_value")
public class AnnouncementFieldValue {
    @TableId(value = "value_id", type = IdType.AUTO)
    private Integer valueId;
    @TableField("field_id")
    private Integer fieldId;
    @TableField("user_id")
    private Integer userId;
    @TableField("field_value")
    private String fieldValue;
    @TableField("updated_at")
    private Date updatedAt;
}
