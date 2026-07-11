-- SmartSeckill 测试数据脚本 (SQL Server)
-- =============================================
-- 使用方法: 在 init.sql 之后执行此脚本
-- 所有测试用户密码: 123456 (BCrypt加密, 与init.sql使用相同的PasswordEncoder生成)
-- =============================================

USE smartseckill;
GO

-- 防止重复执行：若已有商品分类则跳过（避免下拉框/商品列表出现重复）
IF EXISTS (SELECT 1 FROM product_category WHERE deleted = 0)
BEGIN
    PRINT N'测试数据已存在，跳过插入。如需重新导入请先执行 cleanup-duplicates.sql 或清空相关表。';
    RETURN;
END
GO

-- =============================================
-- 1. 用户数据 (密码均为 123456)
-- =============================================

INSERT INTO sys_user (username, password, phone, email, nickname, avatar, status) VALUES
    ('zhangsan', '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001001', 'zhangsan@qq.com', N'张三', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan', 1),
    ('lisi',     '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001002', 'lisi@163.com',     N'李四', 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi', 1),
    ('wangwu',   '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001003', 'wangwu@gmail.com', N'王五', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu', 1),
    ('zhaoliu',  '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001004', 'zhaoliu@qq.com',   N'赵六', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu', 1),
    ('sunqi',    '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001005', 'sunqi@163.com',     N'孙七', 'https://api.dicebear.com/7.x/avataaars/svg?seed=sunqi', 1),
    ('zhouba',   '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001006', 'zhouba@gmail.com',  N'周八', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhouba', 1),
    ('wujiu',    '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001007', 'wujiu@qq.com',      N'吴九', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wujiu', 1),
    ('testuser', '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001008', 'test@test.com',     N'测试用户', 'https://api.dicebear.com/7.x/avataaars/svg?seed=test', 1),
    ('vipuser',  '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001009', 'vip@vip.com',       N'VIP会员', 'https://api.dicebear.com/7.x/avataaars/svg?seed=vip', 1),
    ('newuser',  '$2b$10$bZ01Pzparhq7tyXf8zl13./10ranKpS5wKqRtLhzfmCjp8A5SHLM2', '13800001010', 'new@test.com',      N'新用户', 'https://api.dicebear.com/7.x/avataaars/svg?seed=new', 1);
GO

-- 绑定角色 (admin 已在 init.sql 中绑定, 这里只绑定普通用户角色)
INSERT INTO sys_user_role (user_id, role_id) VALUES
    (2, 1), (3, 1), (4, 1), (5, 1), (6, 1), (7, 1), (8, 1), (9, 1), (10, 1), (11, 1);
GO

-- =============================================
-- 2. 用户地址
-- =============================================

INSERT INTO user_address (user_id, receiver_name, receiver_phone, province, city, district, detail_address, is_default) VALUES
    (2, N'张三', '13800001001', N'北京市', N'北京市', N'朝阳区', N'望京街道望京SOHO T1 1206室', 1),
    (2, N'张三', '13800001001', N'上海市', N'上海市', N'浦东新区', N'张江高科技园区碧波路690号', 0),
    (3, N'李四', '13800001002', N'广东省', N'深圳市', N'南山区', N'科技园南区粤海街道高新大厦1802', 1),
    (4, N'王五', '13800001003', N'浙江省', N'杭州市', N'余杭区', N'未来科技城阿里巴巴西溪园区', 1),
    (5, N'赵六', '13800001004', N'广东省', N'广州市', N'天河区', N'珠江新城华夏路16号富力盈凯广场3501', 1),
    (6, N'孙七', '13800001005', N'四川省', N'成都市', N'高新区', N'天府大道中段666号环球中心E1 2501', 1),
    (7, N'周八', '13800001006', N'湖北省', N'武汉市', N'洪山区', N'光谷大道光谷金融港A栋801', 1),
    (8, N'吴九', '13800001007', N'江苏省', N'南京市', N'鼓楼区', N'新模范马路66号南京工业大学科技园', 1),
    (9, N'测试用户', '13800001008', N'北京市', N'北京市', N'海淀区', N'中关村大街1号中关村梦想实验室', 1);
GO

-- =============================================
-- 3. 商品分类 (三级分类)
-- =============================================

-- 一级分类
INSERT INTO product_category (parent_id, category_name, icon, sort_order) VALUES
    (0, N'手机数码', '📱', 1),
    (0, N'电脑办公', '💻', 2),
    (0, N'家用电器', '🏠', 3),
    (0, N'食品生鲜', '🍎', 4),
    (0, N'服饰鞋包', '👗', 5);
GO

-- 二级分类
INSERT INTO product_category (parent_id, category_name, icon, sort_order) VALUES
    (1, N'智能手机', '📱', 1),
    (1, N'智能穿戴', '⌚', 2),
    (2, N'笔记本', '💻', 1),
    (2, N'平板电脑', '📋', 2),
    (3, N'厨房电器', '🍳', 1),
    (3, N'生活电器', '🌀', 2),
    (4, N'新鲜水果', '🍊', 1),
    (4, N'肉禽蛋奶', '🥩', 2),
    (5, N'女装', '👚', 1),
    (5, N'男装', '👔', 2);
GO

-- 三级分类
INSERT INTO product_category (parent_id, category_name, icon, sort_order) VALUES
    (6, N'5G手机', '📶', 1),
    (6, N'折叠屏手机', '📲', 2),
    (8, N'轻薄本', '💻', 1),
    (8, N'游戏本', '🎮', 2),
    (10, N'空气炸锅', '🍟', 1),
    (10, N'电饭煲', '🍚', 2);
GO

-- =============================================
-- 4. 品牌
-- =============================================

INSERT INTO product_brand (brand_name, logo, description, sort_order) VALUES
    ('Apple',  'https://img.example.com/brand/apple.png',  N'苹果公司，全球知名科技企业', 1),
    ('Samsung', 'https://img.example.com/brand/samsung.png', N'三星电子，韩国科技巨头', 2),
    ('Huawei',  'https://img.example.com/brand/huawei.png',  N'华为技术有限公司', 3),
    ('Xiaomi',  'https://img.example.com/brand/xiaomi.png',  N'小米科技，为发烧而生', 4),
    ('Dell',    'https://img.example.com/brand/dell.png',    N'戴尔科技，IT解决方案提供商', 5),
    ('Lenovo',  'https://img.example.com/brand/lenovo.png',  N'联想集团，全球PC领导者', 6),
    ('Midea',   'https://img.example.com/brand/midea.png',   N'美的集团，智慧生活可以更美的', 7),
    ('Haier',   'https://img.example.com/brand/haier.png',   N'海尔智家，真诚到永远', 8);
GO

-- =============================================
-- 5. SPU (标准产品单元)
-- =============================================

INSERT INTO product_spu (spu_name, category_id, brand_id, description, main_image, images, status) VALUES
    ('iPhone 15 Pro Max', 16, 1,
     N'搭载A17 Pro芯片，钛金属设计，4800万像素主摄，支持USB-C接口',
     'https://img.example.com/spu/iphone15pm.jpg',
     '["https://img.example.com/spu/iphone15pm_1.jpg","https://img.example.com/spu/iphone15pm_2.jpg","https://img.example.com/spu/iphone15pm_3.jpg"]', 1),

    ('Huawei Mate 60 Pro', 16, 3,
     N'搭载麒麟9000S芯片，卫星通话，超聚光XMAGE影像，昆仑玻璃',
     'https://img.example.com/spu/mate60pro.jpg',
     '["https://img.example.com/spu/mate60pro_1.jpg","https://img.example.com/spu/mate60pro_2.jpg"]', 1),

    ('Xiaomi 14 Pro', 16, 4,
     N'骁龙8 Gen 3处理器，徕卡光学镜头，澎湃OS系统，120W秒充',
     'https://img.example.com/spu/xiaomi14pro.jpg',
     '["https://img.example.com/spu/xiaomi14pro_1.jpg","https://img.example.com/spu/xiaomi14pro_2.jpg"]', 1),

    ('Samsung Galaxy S24 Ultra', 16, 2,
     N'骁龙8 Gen 3 for Galaxy，钛金属框架，Galaxy AI，S Pen',
     'https://img.example.com/spu/s24ultra.jpg',
     '["https://img.example.com/spu/s24ultra_1.jpg","https://img.example.com/spu/s24ultra_2.jpg"]', 1),

    ('MacBook Pro 16 M3 Max', 18, 1,
     N'Apple M3 Max芯片，16英寸Liquid Retina XDR显示屏，36GB统一内存',
     'https://img.example.com/spu/macbook16.jpg',
     '["https://img.example.com/spu/macbook16_1.jpg","https://img.example.com/spu/macbook16_2.jpg"]', 1),

    ('Dell XPS 15', 18, 5,
     N'13代酷睿i7处理器，15.6英寸3.5K OLED屏，RTX 4060显卡',
     'https://img.example.com/spu/dellxps15.jpg',
     '["https://img.example.com/spu/dellxps15_1.jpg"]', 1),

    ('Lenovo ThinkPad X1 Carbon', 18, 6,
     N'13代酷睿i7处理器，14英寸2.8K OLED屏，轻至1.12kg，军规认证',
     'https://img.example.com/spu/thinkpadx1.jpg',
     '["https://img.example.com/spu/thinkpadx1_1.jpg","https://img.example.com/spu/thinkpadx1_2.jpg"]', 1),

    ('Apple Watch Ultra 2', 7, 1,
     N'S9芯片，49mm钛金属表壳，精准双频GPS，2000尼特亮度',
     'https://img.example.com/spu/watchultra2.jpg',
     '["https://img.example.com/spu/watchultra2_1.jpg"]', 1),

    (N'美的空气炸锅 KZE5006', 20, 7,
     N'5.5L大容量，360度热风循环，无油健康烹饪，智能触控面板',
     'https://img.example.com/spu/mideaairfryer.jpg',
     '["https://img.example.com/spu/mideaairfryer_1.jpg"]', 1),

    (N'海尔冰箱 BCD-536WGHTDD9', 10, 8,
     N'536升法式多门，风冷无霜，干湿分储，双变频节能',
     'https://img.example.com/spu/haierfridge.jpg',
     '["https://img.example.com/spu/haierfridge_1.jpg","https://img.example.com/spu/haierfridge_2.jpg"]', 1),

    (N'新鲜智利车厘子 JJ级 2.5kg装', 12, NULL,
     N'智利进口，JJ级大果径28-30mm，新鲜直达，甜脆多汁',
     'https://img.example.com/spu/cherry.jpg',
     '["https://img.example.com/spu/cherry_1.jpg"]', 1),

    (N'澳洲M9和牛牛排 200g*4片', 13, NULL,
     N'澳洲原装进口，M9+等级和牛，大理石花纹，入口即化',
     'https://img.example.com/spu/wagyu.jpg',
     '["https://img.example.com/spu/wagyu_1.jpg"]', 1);
GO

-- =============================================
-- 6. SKU (最小库存单位)
-- =============================================

-- iPhone 15 Pro Max SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (1, N'iPhone 15 Pro Max 256GB 原色钛金属', 'IP15PM-256-NT', 9999.00, 9999.00, '{"颜色":"原色钛金属","存储":"256GB"}', 1),
    (1, N'iPhone 15 Pro Max 256GB 蓝色钛金属', 'IP15PM-256-BL', 9999.00, 9999.00, '{"颜色":"蓝色钛金属","存储":"256GB"}', 1),
    (1, N'iPhone 15 Pro Max 512GB 原色钛金属', 'IP15PM-512-NT', 11999.00, 11999.00, '{"颜色":"原色钛金属","存储":"512GB"}', 1),
    (1, N'iPhone 15 Pro Max 1TB 白色钛金属',  'IP15PM-1TB-WT', 13999.00, 13999.00, '{"颜色":"白色钛金属","存储":"1TB"}', 1);
GO

-- Huawei Mate 60 Pro SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (2, N'Huawei Mate 60 Pro 12+512GB 雅丹黑', 'M60P-512-BK', 6999.00, 6999.00, '{"颜色":"雅丹黑","存储":"12+512GB"}', 1),
    (2, N'Huawei Mate 60 Pro 12+256GB 白沙银', 'M60P-256-WH', 6499.00, 6499.00, '{"颜色":"白沙银","存储":"12+256GB"}', 1);
GO

-- Xiaomi 14 Pro SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (3, N'Xiaomi 14 Pro 16+512GB 岩石青', 'MI14P-512-GN', 4999.00, 5499.00, '{"颜色":"岩石青","存储":"16+512GB"}', 1),
    (3, N'Xiaomi 14 Pro 16+1TB 黑色',     'MI14P-1TB-BK', 5599.00, 5999.00, '{"颜色":"黑色","存储":"16+1TB"}', 1);
GO

-- Samsung S24 Ultra SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (4, N'Samsung Galaxy S24 Ultra 12+256GB 钛灰', 'S24U-256-GY', 9699.00, 10199.00, '{"颜色":"钛灰","存储":"12+256GB"}', 1),
    (4, N'Samsung Galaxy S24 Ultra 12+512GB 钛黑', 'S24U-512-BK', 10699.00, 11199.00, '{"颜色":"钛黑","存储":"12+512GB"}', 1);
GO

-- MacBook Pro SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (5, N'MacBook Pro 16 M3 Max 36GB/1TB 深空黑', 'MBP16-M3M-36-1TB', 27999.00, 27999.00, '{"颜色":"深空黑","内存":"36GB","存储":"1TB"}', 1),
    (5, N'MacBook Pro 16 M3 Max 48GB/2TB 银色',   'MBP16-M3M-48-2TB', 34999.00, 34999.00, '{"颜色":"银色","内存":"48GB","存储":"2TB"}', 1);
GO

-- Dell XPS 15 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (6, N'Dell XPS 15 i7/16GB/512GB/RTX4060', 'XPS15-I7-16-512', 12999.00, 13999.00, '{"处理器":"i7-13700H","内存":"16GB","存储":"512GB"}', 1);
GO

-- ThinkPad X1 Carbon SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (7, N'ThinkPad X1 Carbon i7/32GB/1TB 沉浸黑', 'X1C-I7-32-1TB', 14999.00, 15999.00, '{"颜色":"沉浸黑","内存":"32GB","存储":"1TB"}', 1);
GO

-- Apple Watch Ultra 2 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (8, N'Apple Watch Ultra 2 GPS+蜂窝 高山回环', 'AWU2-ALPINE', 6499.00, 6499.00, '{"表带":"高山回环式","尺寸":"49mm"}', 1),
    (8, N'Apple Watch Ultra 2 GPS+蜂窝 海洋表带', 'AWU2-OCEAN',  6499.00, 6499.00, '{"表带":"海洋表带","尺寸":"49mm"}', 1);
GO

-- 美的空气炸锅 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (9, N'美的空气炸锅 KZE5006 5.5L 黑色', 'MD-KZE5006-BK', 399.00, 599.00, '{"颜色":"黑色","容量":"5.5L"}', 1);
GO

-- 海尔冰箱 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (10, N'海尔冰箱 BCD-536 536L 星蕴银', 'HR-BCD536-SL', 6999.00, 7999.00, '{"颜色":"星蕴银","容量":"536L"}', 1);
GO

-- 车厘子 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (11, N'智利车厘子 JJ级 2.5kg 礼盒装', 'CHERRY-JJ-2.5KG', 199.00, 299.00, '{"规格":"JJ级 28-30mm","重量":"2.5kg"}', 1);
GO

-- 和牛 SKU
INSERT INTO product_sku (spu_id, sku_name, sku_code, price, market_price, spec_info, status) VALUES
    (12, N'澳洲M9和牛牛排 200g×4片', 'WAGYU-M9-200G4', 399.00, 599.00, '{"等级":"M9+","规格":"200g×4片"}', 1);
GO

-- =============================================
-- 7. 库存数据
-- =============================================

-- 普通库存
INSERT INTO product_stock (sku_id, stock, lock_stock, stock_type, version) VALUES
    (1, 500, 0, 0, 0),   -- iPhone 15PM 256GB 原色
    (2, 300, 0, 0, 0),   -- iPhone 15PM 256GB 蓝色
    (3, 200, 0, 0, 0),   -- iPhone 15PM 512GB 原色
    (4, 100, 0, 0, 0),   -- iPhone 15PM 1TB 白色
    (5, 400, 0, 0, 0),   -- Mate60Pro 512GB 雅丹黑
    (6, 350, 0, 0, 0),   -- Mate60Pro 256GB 白沙银
    (7, 600, 0, 0, 0),   -- Xiaomi 14 Pro 512GB 岩石青
    (8, 300, 0, 0, 0),   -- Xiaomi 14 Pro 1TB 黑色
    (9, 150, 0, 0, 0),   -- S24 Ultra 256GB 钛灰
    (10, 100, 0, 0, 0),  -- S24 Ultra 512GB 钛黑
    (11, 50, 0, 0, 0),   -- MacBook Pro 36GB/1TB
    (12, 30, 0, 0, 0),   -- MacBook Pro 48GB/2TB
    (13, 80, 0, 0, 0),   -- Dell XPS 15
    (14, 60, 0, 0, 0),   -- ThinkPad X1 Carbon
    (15, 200, 0, 0, 0),  -- Apple Watch Ultra 2 高山回环
    (16, 200, 0, 0, 0),  -- Apple Watch Ultra 2 海洋表带
    (17, 1000, 0, 0, 0), -- 美的空气炸锅
    (18, 150, 0, 0, 0),  -- 海尔冰箱
    (19, 2000, 0, 0, 0), -- 车厘子
    (20, 500, 0, 0, 0);  -- 澳洲和牛
GO

-- =============================================
-- 8. 秒杀活动
-- =============================================

-- 即将开始的活动 (start_time 在未来)
INSERT INTO seckill_activity (activity_name, sku_id, spu_id, seckill_price, original_price, total_stock, available_stock, limit_per_user, start_time, end_time, status) VALUES
    (N'【超级秒杀】iPhone 15 Pro Max 256GB', 1, 1, 7999.00, 9999.00, 100, 100, 1,
     DATEADD(DAY, 1, GETDATE()), DATEADD(DAY, 1, DATEADD(HOUR, 2, GETDATE())), 0),

    (N'【限时抢购】Huawei Mate 60 Pro', 5, 2, 5499.00, 6999.00, 50, 50, 1,
     DATEADD(DAY, 3, GETDATE()), DATEADD(DAY, 3, DATEADD(HOUR, 3, GETDATE())), 0),

    (N'【品牌日】Xiaomi 14 Pro 16+512GB', 7, 3, 3999.00, 4999.00, 80, 80, 2,
     DATEADD(DAY, 2, GETDATE()), DATEADD(DAY, 2, DATEADD(HOUR, 1, GETDATE())), 0);
GO

-- 进行中的秒杀 (start_time 在过去, end_time 在未来)
INSERT INTO seckill_activity (activity_name, sku_id, spu_id, seckill_price, original_price, total_stock, available_stock, limit_per_user, start_time, end_time, status) VALUES
    (N'【火爆进行中】Apple Watch Ultra 2', 15, 8, 4999.00, 6499.00, 30, 8, 1,
     DATEADD(HOUR, -1, GETDATE()), DATEADD(HOUR, 5, GETDATE()), 1),

    (N'【今日特价】美的空气炸锅', 17, 9, 269.00, 399.00, 200, 156, 2,
     DATEADD(HOUR, -3, GETDATE()), DATEADD(HOUR, 9, GETDATE()), 1),

    (N'【限时疯抢】智利车厘子 JJ级', 19, 11, 99.00, 199.00, 500, 212, 3,
     DATEADD(HOUR, -6, GETDATE()), DATEADD(HOUR, 18, GETDATE()), 1);
GO

-- 已结束的活动
INSERT INTO seckill_activity (activity_name, sku_id, spu_id, seckill_price, original_price, total_stock, available_stock, limit_per_user, start_time, end_time, status) VALUES
    (N'【已结束】Samsung S24 Ultra 首发', 9, 4, 7999.00, 9699.00, 20, 0, 1,
     DATEADD(DAY, -3, GETDATE()), DATEADD(DAY, -2, GETDATE()), 2),

    (N'【已结束】Dell XPS 15 秒杀专场', 13, 6, 9999.00, 12999.00, 10, 0, 1,
     DATEADD(DAY, -5, GETDATE()), DATEADD(DAY, -5, DATEADD(HOUR, 2, GETDATE())), 2),

    (N'【已结束】澳洲M9和牛牛排', 20, 12, 299.00, 399.00, 100, 0, 2,
     DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, -1, GETDATE()), 2);
GO

-- 秒杀库存
INSERT INTO product_stock (sku_id, stock, lock_stock, stock_type, activity_id, version) VALUES
    (1, 100, 0, 1, 1, 0),    -- iPhone 秒杀库存
    (5, 50, 0, 1, 2, 0),     -- Huawei 秒杀库存
    (7, 80, 0, 1, 3, 0),     -- Xiaomi 秒杀库存
    (15, 30, 22, 1, 4, 0),   -- Apple Watch 秒杀库存 (进行中, 已锁定22)
    (17, 200, 44, 1, 5, 0),  -- 空气炸锅 秒杀库存 (进行中, 已锁定44)
    (19, 500, 288, 1, 6, 0), -- 车厘子 秒杀库存 (进行中, 已锁定288)
    (9, 20, 20, 1, 7, 0),    -- Samsung 秒杀库存 (已结束, 全部锁定)
    (13, 10, 10, 1, 8, 0),   -- Dell 秒杀库存 (已结束, 全部锁定)
    (20, 100, 100, 1, 9, 0); -- 和牛 秒杀库存 (已结束, 全部锁定)
GO

-- =============================================
-- 9. 优惠券
-- =============================================

INSERT INTO coupon (coupon_name, coupon_type, amount, discount, min_amount, total_count, used_count, start_time, end_time, status) VALUES
    (N'新人专享 满100减20', 1, 20.00, NULL, 100.00, 1000, 156,
     DATEADD(DAY, -30, GETDATE()), DATEADD(DAY, 60, GETDATE()), 1),

    (N'数码品类 满1000减100', 1, 100.00, NULL, 1000.00, 500, 89,
     DATEADD(DAY, -15, GETDATE()), DATEADD(DAY, 45, GETDATE()), 1),

    (N'全场通用 9折券 (最高减200)', 2, NULL, 0.90, 0.00, 2000, 423,
     DATEADD(DAY, -7, GETDATE()), DATEADD(DAY, 30, GETDATE()), 1),

    (N'双十一 满5000减500', 1, 500.00, NULL, 5000.00, 300, 0,
     DATEADD(DAY, -1, GETDATE()), DATEADD(DAY, 15, GETDATE()), 1),

    (N'VIP专属 满200减50', 1, 50.00, NULL, 200.00, 500, 67,
     DATEADD(DAY, -60, GETDATE()), DATEADD(DAY, -1, GETDATE()), 1),

    (N'年终大促 全场85折 (最高减500)', 2, NULL, 0.85, 0.00, 500, 120,
     DATEADD(DAY, 30, GETDATE()), DATEADD(DAY, 60, GETDATE()), 1);
GO

-- 用户优惠券
INSERT INTO user_coupon (user_id, coupon_id, status) VALUES
    (2, 1, 0), (2, 2, 1), (2, 3, 0),
    (3, 1, 1), (3, 4, 0),
    (4, 1, 0), (4, 2, 0), (4, 3, 1),
    (5, 1, 0), (5, 5, 2),
    (6, 2, 0), (6, 3, 0),
    (7, 1, 1), (7, 4, 0),
    (8, 3, 0);
GO

-- =============================================
-- 10. 订单数据
-- =============================================

-- 已完成订单
INSERT INTO order_info (order_sn, user_id, total_amount, pay_amount, freight_amount, coupon_id, coupon_amount, status, order_type, receiver_name, receiver_phone, receiver_address, payment_time, delivery_time, receive_time) VALUES
    ('ORD20240701000001', 2, 9999.00, 9799.00, 0.00, 2, 100.00, 3, 0,
     N'张三', '13800001001', N'北京市朝阳区望京街道望京SOHO T1 1206室',
     '2024-07-01 10:15:30', '2024-07-02 08:30:00', '2024-07-03 14:20:00'),

    ('ORD20240701000002', 3, 399.00, 369.00, 0.00, 1, 20.00, 3, 0,
     N'李四', '13800001002', N'广东省深圳市南山区科技园南区粤海街道高新大厦1802',
     '2024-07-01 14:22:00', '2024-07-02 09:00:00', '2024-07-03 10:30:00'),

    ('ORD20240703000003', 4, 14999.00, 14499.00, 0.00, NULL, 0.00, 3, 0,
     N'王五', '13800001003', N'浙江省杭州市余杭区未来科技城阿里巴巴西溪园区',
     '2024-07-03 09:45:00', '2024-07-04 10:00:00', '2024-07-05 16:00:00'),

    ('ORD20240704000004', 5, 6499.00, 6399.00, 0.00, 2, 100.00, 3, 0,
     N'赵六', '13800001004', N'广东省广州市天河区珠江新城华夏路16号富力盈凯广场3501',
     '2024-07-04 11:30:00', '2024-07-05 09:00:00', '2024-07-06 13:00:00');
GO

-- 待支付订单
INSERT INTO order_info (order_sn, user_id, total_amount, pay_amount, freight_amount, status, order_type, receiver_name, receiver_phone, receiver_address) VALUES
    ('ORD20240705000005', 6, 6999.00, 6999.00, 0.00, 0, 0,
     N'孙七', '13800001005', N'四川省成都市高新区天府大道中段666号环球中心E1 2501'),

    ('ORD20240705000006', 7, 199.00, 199.00, 0.00, 0, 0,
     N'周八', '13800001006', N'湖北省武汉市洪山区光谷大道光谷金融港A栋801');
GO

-- 已支付/待发货订单
INSERT INTO order_info (order_sn, user_id, total_amount, pay_amount, freight_amount, coupon_id, coupon_amount, status, order_type, receiver_name, receiver_phone, receiver_address, payment_time) VALUES
    ('ORD20240704000007', 8, 4999.00, 4499.00, 0.00, 3, 500.00, 1, 0,
     N'吴九', '13800001007', N'江苏省南京市鼓楼区新模范马路66号南京工业大学科技园',
     '2024-07-04 16:00:00'),

    ('ORD20240705000008', 2, 399.00, 349.00, 0.00, 1, 20.00, 1, 0,
     N'张三', '13800001001', N'上海市浦东新区张江高科技园区碧波路690号',
     '2024-07-05 08:30:00');
GO

-- 已取消订单
INSERT INTO order_info (order_sn, user_id, total_amount, pay_amount, freight_amount, status, order_type, receiver_name, receiver_phone, receiver_address, cancel_time, cancel_reason) VALUES
    ('ORD20240703000009', 3, 27999.00, 27999.00, 0.00, 4, 0,
     N'李四', '13800001002', N'广东省深圳市南山区科技园南区粤海街道高新大厦1802',
     '2024-07-03 20:00:00', N'商品信息有误，重新下单'),

    ('ORD20240704000010', 9, 9699.00, 9599.00, 0.00, 4, 0,
     N'VIP会员', '13800001009', N'北京市海淀区中关村大街1号中关村梦想实验室',
     '2024-07-04 18:30:00', N'不想要了');
GO

-- 秒杀订单
INSERT INTO order_info (order_sn, user_id, total_amount, pay_amount, freight_amount, status, order_type, receiver_name, receiver_phone, receiver_address, payment_time) VALUES
    ('SEC20240705000001', 2, 4999.00, 4999.00, 0.00, 1, 1,
     N'张三', '13800001001', N'北京市朝阳区望京街道望京SOHO T1 1206室',
     '2024-07-05 09:00:05'),

    ('SEC20240705000002', 4, 269.00, 269.00, 0.00, 1, 1,
     N'王五', '13800001003', N'浙江省杭州市余杭区未来科技城阿里巴巴西溪园区',
     '2024-07-05 09:00:08'),

    ('SEC20240705000003', 6, 99.00, 99.00, 0.00, 0, 1,
     N'孙七', '13800001005', N'四川省成都市高新区天府大道中段666号环球中心E1 2501',
     NULL),

    ('SEC20240704000004', 5, 7999.00, 7999.00, 0.00, 3, 1,
     N'赵六', '13800001004', N'广东省广州市天河区珠江新城华夏路16号富力盈凯广场3501',
     '2024-07-04 10:00:03');
GO

-- =============================================
-- 11. 订单明细
-- =============================================

-- 已完成订单的明细
INSERT INTO order_item (order_id, order_sn, user_id, sku_id, spu_id, sku_name, sku_image, sku_price, quantity, total_amount, spec_info) VALUES
    (1, 'ORD20240701000001', 2, 1, 1, N'iPhone 15 Pro Max 256GB 原色钛金属', 'https://img.example.com/spu/iphone15pm.jpg', 9999.00, 1, 9999.00, '{"颜色":"原色钛金属","存储":"256GB"}'),
    (2, 'ORD20240701000002', 3, 17, 9, N'美的空气炸锅 KZE5006 5.5L 黑色', 'https://img.example.com/spu/mideaairfryer.jpg', 399.00, 1, 399.00, '{"颜色":"黑色","容量":"5.5L"}'),
    (3, 'ORD20240703000003', 4, 14, 7, N'ThinkPad X1 Carbon i7/32GB/1TB 沉浸黑', 'https://img.example.com/spu/thinkpadx1.jpg', 14999.00, 1, 14999.00, '{"颜色":"沉浸黑","内存":"32GB","存储":"1TB"}'),
    (4, 'ORD20240704000004', 5, 15, 8, N'Apple Watch Ultra 2 GPS+蜂窝 高山回环', 'https://img.example.com/spu/watchultra2.jpg', 6499.00, 1, 6499.00, '{"表带":"高山回环式","尺寸":"49mm"}');
GO

-- 待支付订单的明细
INSERT INTO order_item (order_id, order_sn, user_id, sku_id, spu_id, sku_name, sku_image, sku_price, quantity, total_amount, spec_info) VALUES
    (5, 'ORD20240705000005', 6, 5, 2, N'Huawei Mate 60 Pro 12+512GB 雅丹黑', 'https://img.example.com/spu/mate60pro.jpg', 6999.00, 1, 6999.00, '{"颜色":"雅丹黑","存储":"12+512GB"}'),
    (6, 'ORD20240705000006', 7, 19, 11, N'智利车厘子 JJ级 2.5kg 礼盒装', 'https://img.example.com/spu/cherry.jpg', 199.00, 1, 199.00, '{"规格":"JJ级 28-30mm","重量":"2.5kg"}'),
    (7, 'ORD20240704000007', 8, 7, 3, N'Xiaomi 14 Pro 16+512GB 岩石青', 'https://img.example.com/spu/xiaomi14pro.jpg', 4999.00, 1, 4999.00, '{"颜色":"岩石青","存储":"16+512GB"}'),
    (8, 'ORD20240705000008', 2, 20, 12, N'澳洲M9和牛牛排 200g×4片', 'https://img.example.com/spu/wagyu.jpg', 399.00, 1, 399.00, '{"等级":"M9+","规格":"200g×4片"}'),
    (9, 'ORD20240703000009', 3, 11, 5, N'MacBook Pro 16 M3 Max 36GB/1TB 深空黑', 'https://img.example.com/spu/macbook16.jpg', 27999.00, 1, 27999.00, '{"颜色":"深空黑","内存":"36GB","存储":"1TB"}'),
    (10, 'ORD20240704000010', 9, 9, 4, N'Samsung Galaxy S24 Ultra 12+256GB 钛灰', 'https://img.example.com/spu/s24ultra.jpg', 9699.00, 1, 9699.00, '{"颜色":"钛灰","存储":"12+256GB"}');
GO

-- 秒杀订单的明细
INSERT INTO order_item (order_id, order_sn, user_id, sku_id, spu_id, sku_name, sku_image, sku_price, quantity, total_amount, spec_info) VALUES
    (11, 'SEC20240705000001', 2, 15, 8, N'Apple Watch Ultra 2 高山回环 [秒杀]', 'https://img.example.com/spu/watchultra2.jpg', 4999.00, 1, 4999.00, '{"表带":"高山回环式","活动":"秒杀价"}'),
    (12, 'SEC20240705000002', 4, 17, 9, N'美的空气炸锅 KZE5006 [秒杀]', 'https://img.example.com/spu/mideaairfryer.jpg', 269.00, 1, 269.00, '{"颜色":"黑色","活动":"秒杀价"}'),
    (13, 'SEC20240705000003', 6, 19, 11, N'智利车厘子 JJ级 [秒杀]', 'https://img.example.com/spu/cherry.jpg', 99.00, 1, 99.00, '{"活动":"秒杀价"}'),
    (14, 'SEC20240704000004', 5, 9, 4, N'Samsung S24 Ultra 钛灰 [秒杀]', 'https://img.example.com/spu/s24ultra.jpg', 7999.00, 1, 7999.00, '{"颜色":"钛灰","活动":"秒杀价"}');
GO

-- =============================================
-- 12. 秒杀订单记录
-- =============================================

INSERT INTO seckill_order (activity_id, user_id, order_id, order_sn, sku_id, seckill_price, quantity, status) VALUES
    (4, 2, 11, 'SEC20240705000001', 15, 4999.00, 1, 1),  -- 已支付
    (5, 4, 12, 'SEC20240705000002', 17, 269.00, 1, 1),   -- 已支付
    (6, 6, 13, 'SEC20240705000003', 19, 99.00, 1, 0),    -- 待支付
    (7, 5, 14, 'SEC20240704000004', 9, 7999.00, 1, 1);   -- 已完成
GO

-- =============================================
-- 13. 订单状态日志
-- =============================================

-- 订单1的状态流转
INSERT INTO order_status_log (order_id, order_sn, from_status, to_status, operator, remark, create_time) VALUES
    (1, 'ORD20240701000001', NULL, 0, N'张三', N'用户下单', '2024-07-01 10:15:30'),
    (1, 'ORD20240701000001', 0, 1, N'张三', N'用户支付成功', '2024-07-01 10:15:35'),
    (1, 'ORD20240701000001', 1, 2, N'系统', N'仓库发货', '2024-07-02 08:30:00'),
    (1, 'ORD20240701000001', 2, 3, N'张三', N'用户确认收货', '2024-07-03 14:20:00');
GO

-- 取消订单的状态日志
INSERT INTO order_status_log (order_id, order_sn, from_status, to_status, operator, remark, create_time) VALUES
    (9, 'ORD20240703000009', NULL, 0, N'李四', N'用户下单', '2024-07-03 19:30:00'),
    (9, 'ORD20240703000009', 0, 4, N'李四', N'用户取消订单', '2024-07-03 20:00:00');
GO

-- =============================================
-- 14. 用户行为数据
-- =============================================

INSERT INTO user_behavior (user_id, sku_id, spu_id, behavior_type, duration, ip_address) VALUES
    -- 张三的浏览+购买行为
    (2, 1, 1, 1, 120, '192.168.1.100'),
    (2, 15, 8, 1, 45, '192.168.1.100'),
    (2, 15, 8, 2, 0, '192.168.1.100'),   -- 收藏
    (2, 15, 8, 3, 0, '192.168.1.100'),    -- 加购
    (2, 15, 8, 4, 0, '192.168.1.100'),    -- 购买(秒杀)

    -- 李四的浏览+购买行为
    (3, 5, 2, 1, 90, '192.168.1.101'),
    (3, 17, 9, 1, 30, '192.168.1.101'),
    (3, 17, 9, 3, 0, '192.168.1.101'),
    (3, 17, 9, 4, 0, '192.168.1.101'),

    -- 王五的浏览+购买行为
    (4, 14, 7, 1, 180, '192.168.1.102'),
    (4, 14, 7, 2, 0, '192.168.1.102'),
    (4, 14, 7, 4, 0, '192.168.1.102'),
    (4, 17, 9, 1, 25, '192.168.1.102'),
    (4, 17, 9, 4, 0, '192.168.1.102'),    -- 购买(秒杀)

    -- 赵六的浏览+秒杀
    (5, 9, 4, 1, 100, '192.168.1.103'),
    (5, 9, 4, 2, 0, '192.168.1.103'),
    (5, 9, 4, 4, 0, '192.168.1.103'),

    -- 孙七的浏览行为
    (6, 19, 11, 1, 20, '192.168.1.104'),
    (6, 19, 11, 3, 0, '192.168.1.104'),
    (6, 19, 11, 4, 0, '192.168.1.104'),

    -- 更多浏览数据(用于推荐算法)
    (2, 3, 1, 1, 60, '192.168.1.100'),
    (2, 7, 3, 1, 45, '192.168.1.100'),
    (3, 1, 1, 1, 75, '192.168.1.101'),
    (3, 6, 2, 1, 55, '192.168.1.101'),
    (4, 10, 4, 1, 40, '192.168.1.102'),
    (4, 11, 5, 1, 90, '192.168.1.102'),
    (5, 16, 8, 1, 35, '192.168.1.103'),
    (5, 18, 10, 1, 50, '192.168.1.103'),
    (6, 20, 12, 1, 30, '192.168.1.104'),
    (7, 2, 1, 1, 65, '192.168.1.105'),
    (7, 8, 3, 1, 40, '192.168.1.105'),
    (8, 12, 5, 1, 120, '192.168.1.106'),
    (8, 13, 6, 1, 85, '192.168.1.106');
GO

-- =============================================
-- 15. 用户信用数据
-- =============================================

INSERT INTO user_credit (user_id, credit_score, risk_level, total_orders, total_seckill_attempts, blocked_count) VALUES
    (2, 95, 0, 3, 1, 0),    -- 张三: 高信用
    (3, 88, 0, 2, 0, 0),    -- 李四
    (4, 92, 0, 2, 1, 0),    -- 王五
    (5, 85, 0, 1, 1, 0),    -- 赵六
    (6, 78, 1, 1, 1, 1),    -- 孙七: 有过一次被拦截
    (7, 90, 0, 1, 0, 0),    -- 周八
    (8, 70, 1, 1, 0, 2),    -- 吴九: 低信用,被拦截过2次
    (9, 95, 0, 1, 0, 0),    -- VIP会员
    (10, 100, 0, 0, 0, 0);  -- 新用户, 完美信用
GO

-- =============================================
-- 完成
-- =============================================
PRINT N'测试数据插入完成！';
PRINT N'共插入: 10个用户, 9条地址, 21个分类, 8个品牌, 12个SPU, 20个SKU, 29条库存记录, 9个秒杀活动, 6张优惠券, 14个订单, 14条订单明细, 4条秒杀订单, 多种用户行为和信用数据';
GO
