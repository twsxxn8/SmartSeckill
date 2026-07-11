package com.smartseckill.seckill.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀订单异步创建消息。
 * <p>
 * 秒杀前端（Redis 预减库存）通过后，发送此消息到 RabbitMQ，
 * 由 {@link com.smartseckill.seckill.consumer.SeckillOrderConsumer} 消费并写入数据库。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀活动 ID */
    private Long activityId;
    /** 用户 ID */
    private Long userId;
    /** SKU ID */
    private Long skuId;
    /** SPU ID */
    private Long spuId;
    /** 秒杀价格 */
    private BigDecimal seckillPrice;
    /** 购买数量（通常为 1） */
    private Integer quantity;
}
