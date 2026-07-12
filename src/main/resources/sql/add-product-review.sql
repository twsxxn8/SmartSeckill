-- ==================== 商品评论表（支持楼中楼回复） ====================
-- 如果旧表存在需重建：
-- DROP TABLE IF EXISTS product_review;

CREATE TABLE product_review (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    spu_id          BIGINT NOT NULL,
    sku_id          BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    order_sn        NVARCHAR(50) NULL,          -- 顶层评论关联订单，回复可为空
    parent_id       BIGINT DEFAULT 0,           -- 被回复评论的ID，0=顶层评论
    root_id         BIGINT DEFAULT 0,           -- 根评论ID（顶层评论的ID），0=顶层评论
    rating          TINYINT NULL CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),  -- 顶层评论必须评分，回复可选
    content         NVARCHAR(1000),
    create_time     DATETIME2 DEFAULT GETDATE(),
    update_time     DATETIME2 DEFAULT GETDATE(),
    deleted         TINYINT DEFAULT 0
);

CREATE INDEX idx_review_spu ON product_review(spu_id, deleted, root_id);
CREATE INDEX idx_review_root ON product_review(root_id, create_time);
CREATE UNIQUE INDEX idx_review_user_order_sku ON product_review(user_id, order_sn, sku_id)
    WHERE order_sn IS NOT NULL AND deleted = 0;

-- 迁移旧数据（如需要）:
-- UPDATE product_review SET parent_id = 0, root_id = 0 WHERE root_id IS NULL;
