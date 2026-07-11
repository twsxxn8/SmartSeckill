package com.smartseckill.user.security;

import com.smartseckill.common.util.JwtUtil;
import com.smartseckill.common.util.RedisUtil;
import io.jsonwebtoken.Claims;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器。
 * <p>
 * 每个 HTTP 请求都会经过此过滤器：
 * <ol>
 *   <li>从 Header {@code Authorization: Bearer xxx} 提取 Token</li>
 *   <li>校验 Token 有效性并解析 userId、username、role</li>
 *   <li>构建 {@link LoginUser} 并放入 {@link SecurityContextHolder}</li>
 *   <li>后续 Controller 可通过 {@code @AuthenticationPrincipal LoginUser} 获取当前用户</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            // 检查 token 是否已在黑名单中（已登出或已刷新）
            if (Boolean.TRUE.equals(redisUtil.hasKey(RedisUtil.TOKEN_BLACKLIST_PREFIX + token))) {
                log.debug("Token found in blacklist, rejected");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                // 一次 parseToken 完成验证+解析，避免 validateToken + parseToken 双重解析
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                LoginUser loginUser = new LoginUser(
                        userId,
                        username,
                        "",
                        Collections.singletonList(new SimpleGrantedAuthority(role))
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.debug("JWT authentication failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization Header 或查询参数中提取 Token */
    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 Authorization Header 提取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 2. 兜底从查询参数提取（浏览器 EventSource/SSE 不支持自定义 Header）
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }
        return null;
    }
}
