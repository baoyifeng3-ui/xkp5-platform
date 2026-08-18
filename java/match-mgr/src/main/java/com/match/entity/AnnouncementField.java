package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("announcement_field")
public class AnnouncementField {
    @TableId(value = "field_id", type = IdType.AUTO)
    private Integer fieldId;
    @TableField("field_key")
    private String fieldKey;
    @TableField("field_name")
    private String fieldName;
    @TableField("field_type")
    private String fieldType;
    private Boolean enabled;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
    @TableField("updated_by")
    private Integer updatedBy;
}
