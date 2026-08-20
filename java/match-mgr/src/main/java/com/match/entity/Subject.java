package com.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("subject")
public class Subject {

    private static final long serialVersionUID = 1L;

    @TableId(value = "subject_id", type = IdType.AUTO)
    private Integer subjectId;

    /**
     * 创建时间
     */
    @TableField(value = "subject_creation_time", fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private Date subjectCreationTime;




    /**
     * 模块
     */
    @TableField("modular")
    private String modular;

    /**
     * 试卷类型 A B
     */
    @TableField("test_paper_type")
    private String testPaperType;


    /**
     * 题目类型   1 2
     */
    @TableField("subject_type")
    private String subjectType;

    @TableField(value = "subject_options", updateStrategy = FieldStrategy.IGNORED)
    private String subjectOptions;

    @TableField(value = "correct_answer", updateStrategy = FieldStrategy.IGNORED)
    private String correctAnswer;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(exist = false)
    private List<String> options;
    /**
     * 题目
     */
    @TableField("subject_name")
    private String subjectName;

    /** 题目分值 */
    @TableField("score")
    private Integer score;
    /**
     * 作答方式
     */
    @TableField(value = "answering", updateStrategy = FieldStrategy.IGNORED)
    private String answering;

    /** 截图要求 */
    @TableField(value = "screenshot_requirement", updateStrategy = FieldStrategy.IGNORED)
    private String screenshotRequirement;


    /**
     * 实操环境
     */
    @TableField(value = "point", updateStrategy = FieldStrategy.IGNORED)
    private String point;

    /**
     * 模块标题
     */
    @TableField("modular_name")
    private String modularName;

    /**
     * 题标
     */
    @TableField(value = "subject_identification", updateStrategy = FieldStrategy.IGNORED)
    private String subjectIdentification;


    /**
     * vscode_url
     */
    @TableField(  exist = false)
    private String vscodeUrl;
    /**
     * cvatUrl
     */
    @TableField(  exist = false)
    private String cvatUrl;
    /**
     * t100Url
     */
    @TableField(  exist = false)
    private String t100Url;
}
