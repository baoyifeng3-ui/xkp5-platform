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
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
      @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;

    /**
     * 账号
     */
    @TableField("user_name")
    private String userName;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    @TableField("remark")
    private String remark;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("must_change_password")
    private Boolean mustChangePassword;

    @TableField("must_complete_profile")
    private Boolean mustCompleteProfile;

    @TableField("is_admin")
    private Boolean isAdmin;

    @TableField("role")
    private String role;

}
