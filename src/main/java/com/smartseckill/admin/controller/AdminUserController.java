package com.smartseckill.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.Result;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.entity.SysUserRole;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.mapper.SysUserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员用户管理控制器。
 * <p>提供用户列表查询、启用/禁用、角色分配、密码重置等功能。</p>
 */
@Tag(name = "管理后台-用户管理")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "用户列表（分页）")
    @GetMapping
    public Result<PageResponse<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(status != null, SysUser::getStatus, status)
                .like(StringUtils.hasText(keyword), SysUser::getUsername, keyword)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 清除密码，不应暴露给前端
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(PageResponse.of(result));
    }

    @Operation(summary = "切换用户启用/禁用状态")
    @PutMapping("/{userId}/status")
    public Result<Void> toggleStatus(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return Result.fail(ResultCode.USER_NOT_FOUND);
        user.setStatus(body.get("status"));
        userMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return Result.fail(ResultCode.USER_NOT_FOUND);
        user.setPassword(passwordEncoder.encode(body.get("password")));
        userMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/{userId}/role")
    public Result<Void> assignRole(@PathVariable Long userId, @RequestBody Map<String, Long> body) {
        Long roleId = body.get("roleId");
        if (roleId == null) return Result.fail(ResultCode.BAD_REQUEST);
        // 删除旧角色，插入新角色
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
        return Result.success();
    }
}
