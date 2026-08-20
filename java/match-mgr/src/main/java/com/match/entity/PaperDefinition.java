package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("paper_definition")
public class PaperDefinition {
    @TableId(value = "paper_type", type = IdType.INPUT)
    private String paperType;
    private Integer createdBy;
}
