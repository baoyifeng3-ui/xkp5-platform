package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

@Data
@TableName("paper_submission")
public class PaperSubmission {
    @TableId(value = "submission_id", type = IdType.AUTO)
    private Integer submissionId;
    @TableField("user_id")
    private Integer userId;
    @TableField("user_name")
    private String userName;
    @TableField("paper_type")
    private String paperType;
    @TableField("revision")
    private Integer revision;
    @TableField("status")
    private String status;
    @TableField("current_flag")
    private Integer currentFlag;
    @TableField("objective_score")
    private Integer objectiveScore;
    @TableField("practical_score")
    private Integer practicalScore;
    @TableField("total_score")
    private Integer totalScore;
    @TableField("submitted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submittedAt;
    @TableField("graded_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date gradedAt;
    @TableField("returned_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date returnedAt;
    @TableField("return_reason")
    private String returnReason;
}
