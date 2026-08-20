package com.match.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @author zhaoyu
 * @since 2022-03-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("train_url")
public class TrainUrl implements Serializable {

    private static final long serialVersionUID = 1L;

      @TableId(value = "train_url_id", type = IdType.AUTO)
    private Integer trainUrlId;

    /**
     * 关联用户表
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 训练推理地址
     */
    @TableField("vscode_url")
    private String vscodeUrl;

    @TableField("cvat_url")
    private String cvatUrl;

    @TableField("t100_url")
    private String t100Url;

}
