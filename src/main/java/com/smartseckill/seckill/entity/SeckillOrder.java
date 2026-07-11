package com.smartseckill.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_order")
public class SeckillOrder extends BaseEntity {

    private Long activityId;
    private Long userId;
    private Long orderId;
    private String orderSn;
    private Long skuId;
    private BigDecimal seckillPrice;
    private Integer quantity;
    private Integer status; // 0=待支付, 1=已支付, 2=已取消
}
