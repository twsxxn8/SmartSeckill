package com.smartseckill.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（jjwt 0.11.x API）。
 * <p>
 * 负责 Access Token 和 Refresh Token 的生成、解析与校验。
 * Token 载荷中包含 userId、username、role 等用户信息，
 * 由 {@link com.smartseckill.user.security.JwtAuthenticationFilter} 在每次请求时解析。
 * </p>
 */
@Slf4j
@Component
public class JwtUtil {

    /** JWT 签名密钥，从 application.yml 读取 */
    @Value("${jwt.secret}")
    private String secret;

    /** Access Token 有效期（毫秒），默认 2 小时 */
    @Value("${jwt.expiration}")
    private long expiration;

    /** Refresh Token 有效期（毫秒），默认 7 天 */
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /** 根据配置的 secret 生成 HMAC-SHA 签名密钥 */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问 Token（用于 API 请求认证）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色（如 ROLE_USER、ROLE_ADMIN）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新 Token（用于 Access Token 过期后换取新 Token）。
     * <p>载荷中标记 type=refresh，不含 role 信息。</p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return Refresh Token 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并验证 Token，返回载荷 Claims。
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims 对象
     * @throws ExpiredJwtException Token 已过期
     * @throws JwtException        Token 无效（签名错误、格式错误等）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("Token expired");
            throw e;
        } catch (JwtException e) {
            log.warn("Invalid token: {}", e.getMessage());
            throw e;
        }
    }

    /** 从 Token 中提取用户 ID */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /** 从 Token 中提取用户名（subject 字段） */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /** 从 Token 中提取角色 */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 验证 Token 是否有效（未过期且签名正确）。
     *
     * @return true=有效，false=无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取 Access Token 过期时间（毫秒），用于返回给前端 */
    public long getExpiration() {
        return expiration;
    }

    /**
     * 从 Token 中提取过期时间（即使 Token 已过期也能提取）。
     * <p>用于计算黑名单 TTL：剩余有效时间 = 过期时间 - 当前时间。</p>
     *
     * @param token JWT 字符串
     * @return 过期时间的毫秒时间戳；解析失败返回 null
     */
    public Long getExpirationFromToken(String token) {
        try {
            // 不验证签名，只解析 claims 获取过期时间
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().getTime();
        } catch (ExpiredJwtException e) {
            // Token 虽已过期，仍可从中提取过期时间
            return e.getClaims().getExpiration().getTime();
        } catch (Exception e) {
            log.warn("Failed to extract expiration from token: {}", e.getMessage());
            return null;
        }
    }
}
