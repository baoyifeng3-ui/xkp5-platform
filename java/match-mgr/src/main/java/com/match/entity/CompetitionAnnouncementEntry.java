package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("competition_announcement_entry")
public class CompetitionAnnouncementEntry {
    @TableId(value = "entry_id", type = IdType.AUTO)
    private Integer entryId;
    private String valuesJson;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
}
