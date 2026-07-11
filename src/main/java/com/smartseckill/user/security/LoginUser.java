package com.smartseckill.user.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 登录用户对象，继承 Spring Security 的 {@link User}。
 * <p>
 * 在 {@link JwtAuthenticationFilter} 中构建，通过 {@code @AuthenticationPrincipal} 注入到 Controller。
 * 扩展了 {@code userId} 字段，因为 Spring Security 默认只有 username。
 * </p>
 */
@Getter
public class LoginUser extends User {

    /** 用户 ID（数据库主键） */
    private final Long userId;

    public LoginUser(Long userId, String username, String password,
                     Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
    }
}
