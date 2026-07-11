package com.smartseckill.user.service;

import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.JwtUtil;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.user.dto.LoginRequest;
import com.smartseckill.user.dto.LoginResponse;
import com.smartseckill.user.dto.RegisterRequest;
import com.smartseckill.user.entity.SysRole;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.entity.SysUserRole;
import com.smartseckill.user.mapper.SysRoleMapper;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 认证业务服务。
 * <p>
 * 负责登录、注册、验证码、Token 刷新的核心逻辑。
 * 登录流程委托 Spring Security 的 {@link AuthenticationManager} 校验密码。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    /**
     * 用户登录。
     * <p>
     * 流程：AuthenticationManager 校验密码 → 生成 JWT Token 对 → 返回 LoginResponse
     * </p>
     */
    public LoginResponse login(LoginRequest request) {
        // Spring Security 标准认证：内部会调用 UserDetailsServiceImpl.loadUserByUsername + BCrypt 比对
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        com.smartseckill.user.security.LoginUser loginUser =
                (com.smartseckill.user.security.LoginUser) authentication.getPrincipal();

        String role = loginUser.getAuthorities().iterator().next().getAuthority();
        String accessToken = jwtUtil.generateToken(loginUser.getUserId(), loginUser.getUsername(), role);
        String refreshToken = jwtUtil.generateRefreshToken(loginUser.getUserId(), loginUser.getUsername());

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtUtil.getExpiration() / 1000,
                loginUser.getUserId(),
                loginUser.getUsername(),
                role
        );
    }

    /**
     * 用户注册。
     * <p>
     * 流程：校验验证码 → 检查用户名重复 → BCrypt 加密密码 → 入库 → 分配默认角色
     * </p>
     */
    @Transactional
    public void register(RegisterRequest request) {
        // 验证验证码（优先手机号，其次邮箱）
        String cachedCode = redisUtil.get(RedisUtil.VERIFY_CODE_PREFIX + request.getPhone());
        if (cachedCode == null || !cachedCode.equals(request.getVerificationCode())) {
            cachedCode = redisUtil.get(RedisUtil.VERIFY_CODE_PREFIX + request.getEmail());
            if (cachedCode == null || !cachedCode.equals(request.getVerificationCode())) {
                throw new BusinessException(ResultCode.VERIFICATION_CODE_ERROR);
            }
        }

        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname());
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 分配默认角色 ROLE_USER（role_id = 1）
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(1L);
        sysUserRoleMapper.insert(userRole);

        log.info("User registered successfully: {}", user.getUsername());
    }

    /** 生成验证码并存入 Redis（5 分钟过期），当前为模拟发送 */
    public void sendVerificationCode(String target) {
        String code = String.format("%06d", new Random().nextInt(1000000));

        redisUtil.set(RedisUtil.VERIFY_CODE_PREFIX + target, code,
                RedisUtil.VERIFY_CODE_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("Verification code sent to {}: {}", target, code);
    }

    /** 用 refreshToken 换取新的 accessToken 和 refreshToken */
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 将旧的 refresh token 加入黑名单，防止重复使用
        addToBlacklist(refreshToken);

        String role = resolvePrimaryRole(userId);

        String newAccessToken = jwtUtil.generateToken(userId, username, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtUtil.getExpiration() / 1000,
                userId,
                username,
                role
        );
    }

    /**
     * 用户退出登录，将 accessToken 和 refreshToken 加入 Redis 黑名单。
     * <p>
     * 黑名单 TTL = token 剩余有效时间，到期后 Redis 自动清理，
     * 避免黑名单无限膨胀。
     * </p>
     *
     * @param accessToken  访问令牌（可为 null）
     * @param refreshToken 刷新令牌（可为 null）
     */
    public void logout(String accessToken, String refreshToken) {
        addToBlacklist(accessToken);
        addToBlacklist(refreshToken);
        log.info("User logged out, tokens blacklisted");
    }

    /**
     * 将单个 token 加入 Redis 黑名单。
     * <p>只对尚未自然过期的 token 操作，已过期的 token 无需加入黑名单。</p>
     */
    private void addToBlacklist(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        Long expireTime = jwtUtil.getExpirationFromToken(token);
        if (expireTime == null) {
            return;
        }
        long remaining = expireTime - System.currentTimeMillis();
        if (remaining > 0) {
            redisUtil.set(RedisUtil.TOKEN_BLACKLIST_PREFIX + token, "1",
                    remaining, TimeUnit.MILLISECONDS);
        }
    }

    /** 从数据库加载用户角色，优先返回 ADMIN 角色以与登录权限保持一致 */
    private String resolvePrimaryRole(Long userId) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(userId);
        if (roles.isEmpty()) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return roles.stream()
                .map(SysRole::getRoleCode)
                .filter("ROLE_ADMIN"::equals)
                .findFirst()
                .orElse(roles.get(0).getRoleCode());
    }
}
