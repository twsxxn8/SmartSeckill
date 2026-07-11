-- Migration: Add deleted column to user_coupon table
-- ====================================================
-- Background: UserCoupon entity originally extended BaseEntity which uses
-- @TableLogic on the 'deleted' field, but the user_coupon table was missing
-- this column. Every query auto-appended "WHERE deleted = 0", causing
-- SQL error → 500 "服务器内部错误" on every checkout.
--
-- Fix: Entity changed to not extend BaseEntity (immediate fix).
-- This migration adds the column for schema consistency; after running it,
-- UserCoupon can safely extend BaseEntity again.

USE smartseckill;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE name = 'deleted' AND OBJECT_NAME(object_id) = 'user_coupon'
)
BEGIN
    ALTER TABLE user_coupon
    ADD deleted TINYINT DEFAULT 0 NOT NULL;

    PRINT N'已为 user_coupon 表添加 deleted 列';
END
ELSE
BEGIN
    PRINT N'user_coupon 表已存在 deleted 列，跳过';
END
GO
