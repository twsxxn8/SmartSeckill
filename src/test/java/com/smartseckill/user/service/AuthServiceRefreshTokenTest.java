package com.smartseckill.user.service;

import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.JwtUtil;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.user.dto.LoginResponse;
import com.smartseckill.user.entity.SysRole;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.mapper.SysRoleMapper;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void refreshToken_adminUser_shouldKeepAdminRole() {
        mockValidRefreshToken(1L, "admin");

        SysUser user = activeUser(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(user);
        when(sysRoleMapper.selectRolesByUserId(1L)).thenReturn(List.of(userRole(), adminRole()));
        when(jwtUtil.generateToken(1L, "admin", "ROLE_ADMIN")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(1L, "admin")).thenReturn("new-refresh");

        LoginResponse response = authService.refreshToken("refresh-token");

        assertEquals("ROLE_ADMIN", response.getRole());
        verify(jwtUtil).generateToken(1L, "admin", "ROLE_ADMIN");
    }

    @Test
    void refreshToken_normalUser_shouldLoadRoleFromDb() {
        mockValidRefreshToken(2L, "alice");

        SysUser user = activeUser(2L);
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectRolesByUserId(2L)).thenReturn(List.of(userRole()));
        when(jwtUtil.generateToken(2L, "alice", "ROLE_USER")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(2L, "alice")).thenReturn("new-refresh");

        LoginResponse response = authService.refreshToken("refresh-token");

        assertEquals("ROLE_USER", response.getRole());
        verify(jwtUtil).generateToken(2L, "alice", "ROLE_USER");
    }

    @Test
    void refreshToken_userWithoutRole_shouldThrowNotFound() {
        mockValidRefreshToken(3L, "ghost");

        SysUser user = activeUser(3L);
        when(sysUserMapper.selectById(3L)).thenReturn(user);
        when(sysRoleMapper.selectRolesByUserId(3L)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refreshToken("refresh-token"));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    private void mockValidRefreshToken(Long userId, String username) {
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("refresh-token")).thenReturn(userId);
        when(jwtUtil.getUsernameFromToken("refresh-token")).thenReturn(username);
        when(jwtUtil.getExpiration()).thenReturn(7200000L);
    }

    private SysUser activeUser(Long userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(1);
        return user;
    }

    private SysRole userRole() {
        SysRole role = new SysRole();
        role.setRoleCode("ROLE_USER");
        return role;
    }

    private SysRole adminRole() {
        SysRole role = new SysRole();
        role.setRoleCode("ROLE_ADMIN");
        return role;
    }
}
