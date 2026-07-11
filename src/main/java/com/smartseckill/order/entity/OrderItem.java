package com.smartseckill.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单项实体，对应表 {@code order_item}。
 * <p>一个订单包含多个 OrderItem，每个 Item 对应一个 SKU。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private String orderSn;
    private Long userId;
    private Long skuId;
    private Long spuId;
    /** 下单时的商品快照（价格、名称等，不受后续商品变更影响） */
    private String skuName;
    private String skuImage;
    private BigDecimal skuPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String specInfo;
}
