package com.smartseckill.user.controller;

import com.smartseckill.common.result.Result;
import com.smartseckill.user.dto.ProfileUpdateRequest;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个人信息控制器。
 * <p>
 * 提供当前登录用户的信息查看与修改功能。
 * 所有操作基于 {@code @AuthenticationPrincipal LoginUser} 确定用户身份。
 * </p>
 */
@Slf4j
@Tag(name = "个人中心")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserMapper sysUserMapper;

    /**
     * 获取当前用户基本信息（不含密码）。
     */
    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<SysUser> getProfile(@AuthenticationPrincipal LoginUser loginUser) {
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user != null) {
            // 安全：不向前端暴露密码
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 更新当前用户个人信息。
     * <p>仅更新传入的非 null 字段（支持部分更新）。</p>
     */
    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<SysUser> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestBody ProfileUpdateRequest request) {
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        boolean changed = false;
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            user.setNickname(request.getNickname());
            changed = true;
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            user.setPhone(request.getPhone());
            changed = true;
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            user.setEmail(request.getEmail());
            changed = true;
        }
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            user.setAvatar(request.getAvatar());
            changed = true;
        }

        if (changed) {
            sysUserMapper.updateById(user);
            log.info("Profile updated: userId={}", loginUser.getUserId());
        }

        user.setPassword(null);
        return Result.success(user);
    }
}
