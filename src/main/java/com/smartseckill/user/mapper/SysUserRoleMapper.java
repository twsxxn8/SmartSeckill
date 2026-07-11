package com.smartseckill.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/** 用户-角色关联 Mapper，对应表 sys_user_role */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
