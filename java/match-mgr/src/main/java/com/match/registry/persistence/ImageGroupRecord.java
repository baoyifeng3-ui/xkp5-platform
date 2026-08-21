package com.match.registry.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_group")
public class ImageGroupRecord {
    @TableId(value = "group_id", type = IdType.INPUT)
    private String groupId;
    private String name;
    private String groupType;
    private String description;
    private String state;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
}
