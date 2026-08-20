package com.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.util.Date;

@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("answer_sheet")
public class AnswerSheet {

    private static final long serialVersionUID = 1L;

    @TableId("answer_id")
    private Integer answerId;


    @TableField("user_id")
    private Integer userId;

    @TableField("subject_id")
    private Integer subjectId;


    @TableField("subject_type")
    private String subjectType;


    @TableField("answer_text")
    private String answerText;

    @TableField("answer_img")
    private String answerImg;


    /**
     *
     */
    @TableField(value = "creation_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private Date creationTime;

}
