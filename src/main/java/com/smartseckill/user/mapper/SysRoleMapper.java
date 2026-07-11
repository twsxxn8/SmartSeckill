package com.smartseckill.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 系统角色 Mapper */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 根据用户 ID 查询其拥有的所有角色（联表 sys_user_role） */
    @Select("SELECT r.* FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
