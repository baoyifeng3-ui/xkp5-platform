package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("system_setting")
public class SystemSetting {
    @TableId(value = "setting_key", type = IdType.INPUT)
    private String settingKey;
    private String settingValue;
    private Integer updatedBy;
}
