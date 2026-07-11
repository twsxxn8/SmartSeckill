package com.smartseckill.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体，对应表 {@code sys_role}。
 * <p>roleCode 如 ROLE_USER、ROLE_ADMIN，用于 Spring Security 权限判断。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称（展示用） */
    private String roleName;
    /** 角色编码（权限标识，如 ROLE_ADMIN） */
    private String roleCode;
    private String description;
}
