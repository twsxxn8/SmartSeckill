-- SmartSeckill 密码修复脚本 (SQL Server)
-- =============================================
-- 用途: 修复数据库中已存在的错误密码哈希
-- 将 admin 和所有测试用户的密码统一重置为正确的 BCrypt 哈希
-- 执行后: admin → admin123, 其他用户 → 123456
-- =============================================

USE smartseckill;
GO

-- 修复 admin 密码: admin123
UPDATE sys_user SET password = '$2b$10$pXqtVPc3SRTTN01J3UXSHuVu5192Rdme0RxXgrry.qly3NXsCoCUu', update_time = GETDATE()
WHERE username = 'admin';
GO

-- 修复所有测试用户密码: 123456
UPDATE sys_user SET password = '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', update_time = GETDATE()
WHERE username IN ('zhangsan', 'lisi', 'wangwu', 'zhaoliu', 'sunqi', 'zhouba', 'wujiu', 'testuser', 'vipuser', 'newuser');
GO

-- 验证结果
SELECT username, password, update_time FROM sys_user WHERE deleted = 0;
GO

PRINT N'密码修复完成！';
PRINT N'  admin    → admin123';
PRINT N'  测试用户 → 123456';
GO
