-- ==================== 订单幂等提交令牌（防重复下单） ====================
-- 背景：用户下单后响应丢失/前端超时，重复点击会在系统中产生多笔订单并被重复支付。
-- submit_token 由 POST /order/token 签发（一次性，10 分钟有效），提交订单时携带；
-- Redis SETNX 是第一道防线，本过滤唯一索引是最终兜底：
-- Redis 失效/并发穿透时，第二笔相同 token 的 insert 直接冲突，事务回滚后转为返回首单。

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('order_info') AND name = 'submit_token')
ALTER TABLE order_info ADD submit_token NVARCHAR(64) NULL;   -- 一次性下单令牌，仅普通订单使用
GO

-- 过滤唯一索引：秒杀订单 submit_token 为 NULL，不参与唯一约束（SQL Server 唯一索引默认只允许一个 NULL）
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ux_order_info_submit_token' AND object_id = OBJECT_ID('order_info'))
CREATE UNIQUE INDEX ux_order_info_submit_token ON order_info(submit_token)
    WHERE submit_token IS NOT NULL;
GO
