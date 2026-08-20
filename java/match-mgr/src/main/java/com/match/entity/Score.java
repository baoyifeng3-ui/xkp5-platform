package com.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 
 * </p>
 *
 * @author zhaoyu
 * @since 2022-03-22
 */
@Data
@Component
@EqualsAndHashCode(callSuper = false)
@TableName("score")
public class Score implements Serializable {

    private static final long serialVersionUID = 1L;

      @TableId(value = "score_id", type = IdType.AUTO)
    private Integer scoreId;

    /**
     * 关联用户表
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 得分
     */
    @TableField("score")
    private Double score;

    /**
     * 创建日期
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private Date createTime;

    /**
     * 图片字节码
     */
    @TableField(  exist = false)
    private StringBuffer[] img;

    @TableField(  exist = false)
    private User user;




}
