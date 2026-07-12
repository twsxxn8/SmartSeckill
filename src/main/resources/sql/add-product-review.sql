-- ==================== 商品评论表 ====================
CREATE TABLE product_review (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    spu_id          BIGINT NOT NULL,
    sku_id          BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    order_sn        NVARCHAR(50) NOT NULL,
    rating          TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content         NVARCHAR(1000),
    create_time     DATETIME2 DEFAULT GETDATE(),
    update_time     DATETIME2 DEFAULT GETDATE(),
    deleted         TINYINT DEFAULT 0
);

CREATE INDEX idx_review_spu ON product_review(spu_id, deleted);
CREATE UNIQUE INDEX idx_review_user_order_sku ON product_review(user_id, order_sn, sku_id);

-- 插入一些示例评论测试数据（可选）
-- INSERT INTO product_review (spu_id, sku_id, user_id, order_sn, rating, content)
-- VALUES (1, 1, 2, '20240101001', 5, '非常好用，性价比很高！');
