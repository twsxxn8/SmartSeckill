package com.smartseckill.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 系统用户 Mapper，继承 BaseMapper 获得基础 CRUD */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
