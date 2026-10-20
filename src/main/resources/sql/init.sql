-- SmartSeckill Database Init Script for SQL Server
-- =============================================

-- 创建数据库
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'smartseckill')
BEGIN
    CREATE DATABASE smartseckill;
END
GO

USE smartseckill;
GO

-- =============================================
-- 1. 用户与认证模块
-- =============================================

-- 用户表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_user' AND xtype='U')
CREATE TABLE sys_user (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username NVARCHAR(50) NOT NULL UNIQUE,
    password NVARCHAR(255) NOT NULL,
    phone NVARCHAR(20),
    email NVARCHAR(100),
    nickname NVARCHAR(50),
    avatar NVARCHAR(500),
    status TINYINT DEFAULT 1,            -- 1=正常, 0=禁用
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 角色表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_role' AND xtype='U')
CREATE TABLE sys_role (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_name NVARCHAR(50) NOT NULL,
    role_code NVARCHAR(50) NOT NULL UNIQUE,
    description NVARCHAR(255),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 用户角色关联表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_user_role' AND xtype='U')
CREATE TABLE sys_user_role (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT uk_user_role UNIQUE(user_id, role_id)
);
GO

-- 用户地址表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_address' AND xtype='U')
CREATE TABLE user_address (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name NVARCHAR(50) NOT NULL,
    receiver_phone NVARCHAR(20) NOT NULL,
    province NVARCHAR(50),
    city NVARCHAR(50),
    district NVARCHAR(50),
    detail_address NVARCHAR(255) NOT NULL,
    is_default TINYINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 2. 商品管理模块
-- =============================================

-- 商品分类表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='product_category' AND xtype='U')
CREATE TABLE product_category (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    category_name NVARCHAR(100) NOT NULL,
    icon NVARCHAR(500),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 品牌表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='product_brand' AND xtype='U')
CREATE TABLE product_brand (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    brand_name NVARCHAR(100) NOT NULL,
    logo NVARCHAR(500),
    description NVARCHAR(500),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- SPU表（标准产品单元）
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='product_spu' AND xtype='U')
CREATE TABLE product_spu (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    spu_name NVARCHAR(200) NOT NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT,
    description NVARCHAR(2000),
    main_image NVARCHAR(500),
    images NVARCHAR(2000),
    status TINYINT DEFAULT 0,          -- 0=草稿, 1=已上架, 2=已下架
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- SKU表（最小库存单位）
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='product_sku' AND xtype='U')
CREATE TABLE product_sku (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    spu_id BIGINT NOT NULL,
    sku_name NVARCHAR(200) NOT NULL,
    sku_code NVARCHAR(100),
    price DECIMAL(10,2) NOT NULL,
    market_price DECIMAL(10,2),
    image NVARCHAR(500),
    spec_info NVARCHAR(500),           -- JSON: {"颜色":"红色","内存":"128G"}
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 库存表（普通商品与秒杀商品隔离）
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='product_stock' AND xtype='U')
CREATE TABLE product_stock (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    lock_stock INT NOT NULL DEFAULT 0,  -- 锁定库存
    stock_type TINYINT DEFAULT 0,       -- 0=普通库存, 1=秒杀库存
    activity_id BIGINT DEFAULT 0,       -- 秒杀活动ID（秒杀库存时关联，默认0表示普通库存）
    version INT DEFAULT 0,              -- 乐观锁版本号
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT uk_sku_stock_type UNIQUE(sku_id, stock_type, activity_id)
);
GO

-- =============================================
-- 3. 订单模块
-- =============================================

-- 订单主表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='order_info' AND xtype='U')
CREATE TABLE order_info (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_sn NVARCHAR(64) NOT NULL UNIQUE,   -- 订单编号
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,     -- 总金额
    pay_amount DECIMAL(10,2),                -- 实付金额
    freight_amount DECIMAL(10,2) DEFAULT 0,  -- 运费
    coupon_id BIGINT,
    coupon_amount DECIMAL(10,2) DEFAULT 0,   -- 优惠券抵扣
    status TINYINT DEFAULT 0,                -- 0=待支付,1=已支付,2=已发货,3=已完成,4=已取消
    order_type TINYINT DEFAULT 0,            -- 0=普通订单, 1=秒杀订单
    receiver_name NVARCHAR(50),
    receiver_phone NVARCHAR(20),
    receiver_address NVARCHAR(255),
    payment_time DATETIME2,
    delivery_time DATETIME2,
    receive_time DATETIME2,
    cancel_time DATETIME2,
    cancel_reason NVARCHAR(255),
    remark NVARCHAR(500),
    submit_token NVARCHAR(64) NULL,          -- 一次性下单令牌（幂等防重复提交，秒杀订单为 NULL）
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 订单明细表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='order_item' AND xtype='U')
CREATE TABLE order_item (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_sn NVARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_name NVARCHAR(200),
    sku_image NVARCHAR(500),
    sku_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    spec_info NVARCHAR(500),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 订单状态日志
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='order_status_log' AND xtype='U')
CREATE TABLE order_status_log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_sn NVARCHAR(64) NOT NULL,
    from_status TINYINT,
    to_status TINYINT NOT NULL,
    operator NVARCHAR(50),
    remark NVARCHAR(255),
    create_time DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 4. 秒杀模块
-- =============================================

-- 秒杀活动表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='seckill_activity' AND xtype='U')
CREATE TABLE seckill_activity (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    activity_name NVARCHAR(200) NOT NULL,
    sku_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    limit_per_user INT DEFAULT 1,        -- 每人限购
    start_time DATETIME2 NOT NULL,
    end_time DATETIME2 NOT NULL,
    status TINYINT DEFAULT 0,            -- 0=未开始,1=进行中,2=已结束,3=已取消
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 秒杀订单表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='seckill_order' AND xtype='U')
CREATE TABLE seckill_order (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT,
    order_sn NVARCHAR(64),
    sku_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    quantity INT DEFAULT 1,
    status TINYINT DEFAULT 0,            -- 0=待支付,1=已支付,2=已取消
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT uk_activity_user UNIQUE(activity_id, user_id)
);
GO

-- =============================================
-- 5. 推荐模块 - 用户行为表
-- =============================================

IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_behavior' AND xtype='U')
CREATE TABLE user_behavior (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    behavior_type TINYINT NOT NULL,      -- 1=浏览,2=收藏,3=加购,4=购买
    duration INT DEFAULT 0,              -- 页面停留时长(秒)
    device_fingerprint NVARCHAR(128),
    ip_address NVARCHAR(50),
    create_time DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 6. 风控模块
-- =============================================

-- 用户信用表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_credit' AND xtype='U')
CREATE TABLE user_credit (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    credit_score INT DEFAULT 100,        -- 信用分（0-100）
    risk_level TINYINT DEFAULT 0,        -- 0=低风险,1=中风险,2=高风险
    total_orders INT DEFAULT 0,
    total_seckill_attempts INT DEFAULT 0,
    blocked_count INT DEFAULT 0,
    last_login_ip NVARCHAR(50),
    last_device_fingerprint NVARCHAR(128),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 风控决策日志表（DELAY/VERIFY/BLOCK 决策经 MQ 异步落库，供审计与规则调优）
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='risk_decision_log' AND xtype='U')
CREATE TABLE risk_decision_log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    client_ip NVARCHAR(50),
    user_agent NVARCHAR(500),
    risk_score INT NOT NULL,             -- 风险评分（0-100）
    risk_level NVARCHAR(20),             -- LOW/MEDIUM/HIGH/CRITICAL
    action VARCHAR(20),                  -- DELAY/VERIFY/BLOCK
    reason NVARCHAR(500),                -- 决策原因
    created_at DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 7. 优惠券表
-- =============================================

IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='coupon' AND xtype='U')
CREATE TABLE coupon (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    coupon_name NVARCHAR(100) NOT NULL,
    coupon_type TINYINT NOT NULL,        -- 1=满减,2=折扣
    amount DECIMAL(10,2),                -- 满减金额
    discount DECIMAL(3,2),               -- 折扣比例
    min_amount DECIMAL(10,2) DEFAULT 0,  -- 使用门槛
    total_count INT NOT NULL,
    used_count INT DEFAULT 0,
    start_time DATETIME2 NOT NULL,
    end_time DATETIME2 NOT NULL,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- 用户优惠券
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_coupon' AND xtype='U')
CREATE TABLE user_coupon (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status TINYINT DEFAULT 0,            -- 0=未使用,1=已使用,2=已过期
    used_time DATETIME2,
    order_id BIGINT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME2 DEFAULT GETDATE(),
    update_time DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 初始化数据
-- =============================================

-- 插入默认角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
    ('普通用户', 'ROLE_USER', '普通用户角色'),
    ('管理员', 'ROLE_ADMIN', '系统管理员角色');
GO

-- 插入管理员账号 (密码: admin123 BCrypt加密)
INSERT INTO sys_user (username, password, nickname, status) VALUES
    ('admin', '$2b$10$pXqtVPc3SRTTN01J3UXSHuVu5192Rdme0RxXgrry.qly3NXsCoCUu', '系统管理员', 1);
GO

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2);
GO

-- 创建索引
CREATE INDEX idx_user_address_user_id ON user_address(user_id);
CREATE INDEX idx_product_sku_spu_id ON product_sku(spu_id);
CREATE INDEX idx_product_stock_sku_id ON product_stock(sku_id);
CREATE INDEX idx_order_info_user_id ON order_info(user_id);
CREATE INDEX idx_order_info_order_sn ON order_info(order_sn);
CREATE UNIQUE INDEX ux_order_info_submit_token ON order_info(submit_token) WHERE submit_token IS NOT NULL;
CREATE INDEX idx_order_item_order_id ON order_item(order_id);
CREATE INDEX idx_seckill_activity_sku_id ON seckill_activity(sku_id);
CREATE INDEX idx_seckill_order_activity_id ON seckill_order(activity_id);
CREATE INDEX idx_seckill_order_user_id ON seckill_order(user_id);
CREATE INDEX idx_user_behavior_user_id ON user_behavior(user_id);
CREATE INDEX idx_user_behavior_sku_id ON user_behavior(sku_id);
CREATE INDEX idx_user_credit_user_id ON user_credit(user_id);
CREATE INDEX idx_order_status_log_order_id ON order_status_log(order_id);
CREATE INDEX idx_risk_decision_log_user_time ON risk_decision_log(user_id, created_at DESC);
GO
