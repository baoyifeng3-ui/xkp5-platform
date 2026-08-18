package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

@Data
@TableName("paper_export_batch")
public class PaperExportBatch {
    @TableId(value = "export_batch_id", type = IdType.AUTO)
    private Integer exportBatchId;
    @TableField("paper_type")
    private String paperType;
    @TableField("created_by")
    private Integer createdBy;
    @TableField("created_by_name")
    private String createdByName;
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    @TableField("export_key")
    private String exportKey;
    @TableField("submission_ids")
    private String submissionIds;
    @TableField("pdf_count")
    private Integer pdfCount;
    @TableField("status")
    private String status;
}
