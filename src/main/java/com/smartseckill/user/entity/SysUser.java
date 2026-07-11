package com.smartseckill.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体，对应表 {@code sys_user}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录用户名，唯一 */
    private String username;
    /** BCrypt 加密后的密码，不可明文存储 */
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    /** 账号状态：1=正常，0=禁用 */
    private Integer status;
}
