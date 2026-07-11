package com.smartseckill.user.controller;

import com.smartseckill.common.result.Result;
import com.smartseckill.user.dto.LoginRequest;
import com.smartseckill.user.dto.LoginResponse;
import com.smartseckill.user.dto.RegisterRequest;
import com.smartseckill.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器。
 * <p>
 * 提供登录、注册、验证码、Token 刷新等接口。
 * 所有接口均为公开访问（{@code SecurityConfig} 中 {@code /auth/**} 免登录）。
 * </p>
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户登录，成功后返回 accessToken 和 refreshToken */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 用户注册，需先调用验证码接口获取验证码 */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /** 发送验证码到手机或邮箱（当前为模拟，验证码打印在日志中） */
    @Operation(summary = "发送验证码")
    @PostMapping("/verification-code")
    public Result<Void> sendVerificationCode(@RequestBody Map<String, String> request) {
        String target = request.get("target");
        authService.sendVerificationCode(target);
        return Result.success();
    }

    /** accessToken 过期后，用 refreshToken 换取新的 Token 对 */
    @Operation(summary = "刷新Token")
    @PostMapping("/refresh-token")
    public Result<LoginResponse> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        return Result.success(authService.refreshToken(refreshToken));
    }

    /**
     * 用户退出登录，将 accessToken 和 refreshToken 加入 Redis 黑名单。
     * <p>即使 token 已在前端清除，服务端也会在 token 剩余有效期内拒绝该 token。</p>
     */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, String> body) {
        String accessToken = extractBearerToken(request);
        String refreshToken = body != null ? body.get("refreshToken") : null;
        authService.logout(accessToken, refreshToken);
        return Result.success();
    }

    /** 从 Authorization Header 中提取 Bearer Token */
    private String extractBearerToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
