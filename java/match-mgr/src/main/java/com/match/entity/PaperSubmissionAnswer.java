package com.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("paper_submission_answer")
public class PaperSubmissionAnswer {
    @TableId(value = "submission_answer_id", type = IdType.AUTO)
    private Integer submissionAnswerId;
    @TableField("submission_id")
    private Integer submissionId;
    @TableField("subject_id")
    private Integer subjectId;
    @TableField("subject_type")
    private String subjectType;
    @TableField("modular")
    private String modular;
    @TableField("modular_name")
    private String modularName;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("subject_name")
    private String subjectName;
    @TableField("subject_options")
    private String subjectOptions;
    @TableField("answering")
    private String answering;
    @TableField("screenshot_requirement")
    private String screenshotRequirement;
    @TableField("max_score")
    private Integer maxScore;
    @TableField("correct_answer")
    private String correctAnswer;
    @TableField("answer_text")
    private String answerText;
    @TableField("answer_img")
    private String answerImg;
    @TableField("awarded_score")
    private Integer awardedScore;
    @TableField("grading_method")
    private String gradingMethod;
}
