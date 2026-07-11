package com.smartseckill.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品库存实体，对应表 {@code product_stock}。
 * <p>
 * 库存模型：stock（总库存）- lockStock（已锁定）= 可用库存。
 * version 字段配合 MyBatis-Plus @Version 实现乐观锁。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_stock")
public class ProductStock extends BaseEntity {

    private Long skuId;
    /** 总库存数量 */
    private Integer stock;
    /** 已被订单锁定、尚未支付的库存 */
    private Integer lockStock;
    /** 库存类型：0=普通库存, 1=秒杀库存 */
    private Integer stockType;
    /** 秒杀活动 ID（stockType=1 时使用，默认为 0 表示普通库存） */
    private Long activityId = 0L;

    @Version
    private Integer version;
}
