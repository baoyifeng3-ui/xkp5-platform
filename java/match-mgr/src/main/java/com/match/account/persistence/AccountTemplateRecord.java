package com.match.account.persistence;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("account_template") public class AccountTemplateRecord { @TableId(value="template_id",type=IdType.INPUT) private String templateId; private String templateName; private String customFields; private Boolean requireProfile; private Integer createdBy; private LocalDateTime createdAt; }
