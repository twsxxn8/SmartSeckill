-- 清理因重复执行 test-data.sql 产生的重复数据
-- 保留 id 较小的原始数据（1-12 SPU, 1-21 分类, 1-8 品牌, 1-20 SKU）
USE smartseckill;
GO

-- 秒杀库存（重复批次 id 21-29）
DELETE FROM product_stock WHERE id BETWEEN 21 AND 29;
GO

-- 秒杀活动（重复批次 id 10-18）
DELETE FROM seckill_activity WHERE id BETWEEN 10 AND 18;
GO

-- SKU（重复批次 id 21-40）
DELETE FROM product_sku WHERE id BETWEEN 21 AND 40;
GO

-- SPU（重复批次 id 13-24，无 SKU 关联）
DELETE FROM product_spu WHERE id BETWEEN 13 AND 24;
GO

-- 分类（重复批次 id 22-42）
DELETE FROM product_category WHERE id BETWEEN 22 AND 42;
GO

-- 品牌（重复批次 id 9-16）
DELETE FROM product_brand WHERE id BETWEEN 9 AND 16;
GO
