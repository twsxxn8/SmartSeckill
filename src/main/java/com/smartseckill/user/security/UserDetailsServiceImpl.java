package com.smartseckill.user.security;

import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.mapper.SysRoleMapper;
import com.smartseckill.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security 用户详情服务。
 * <p>
 * 登录时由 {@link org.springframework.security.authentication.AuthenticationManager} 调用，
 * 根据用户名查数据库、加载角色，返回 {@link LoginUser} 供密码比对。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    /**
     * 根据用户名加载用户信息（Spring Security 登录流程的核心回调）。
     *
     * @param username 登录用户名
     * @return 包含 userId、加密密码、角色权限的 LoginUser
     * @throws UsernameNotFoundException 用户不存在或已被禁用
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0)
        );

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("User is disabled: " + username);
        }

        // 查询用户关联的角色（如 ROLE_USER、ROLE_ADMIN）
        List<SimpleGrantedAuthority> authorities = sysRoleMapper.selectRolesByUserId(user.getId())
                .stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleCode()))
                .collect(Collectors.toList());

        return new LoginUser(user.getId(), user.getUsername(), user.getPassword(), authorities);
    }
}
