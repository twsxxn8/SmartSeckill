package com.smartseckill.order.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.entity.OrderItem;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.order.mapper.OrderItemMapper;
import com.smartseckill.order.mapper.OrderStatusLogMapper;
import com.smartseckill.order.entity.OrderStatusLog;
import com.smartseckill.product.mapper.ProductStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消消费者。
 * <p>
 * 工作原理（TTL + 死信队列）：
 * <ol>
 *   <li>创建订单时，OrderService 向延迟交换机发送消息，TTL=30 分钟</li>
 *   <li>消息在延迟队列中等待，过期后自动转发到死信交换机</li>
 *   <li>本消费者监听死信队列，收到消息后检查订单是否仍为待支付</li>
 *   <li>若是，则释放锁定库存并将订单状态改为已取消</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductStockMapper stockMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final com.smartseckill.seckill.service.SeckillStockRestoreService seckillStockRestoreService;

    /**
     * 监听死信队列，处理超时未支付的订单。
     *
     * @param orderSn 订单编号（消息体）
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_LETTER_QUEUE,
                   concurrency = "1-3")
    @Transactional
    public void handleOrderTimeout(String orderSn) {
        log.info("Received order timeout message: orderSn={}", orderSn);

        OrderInfo order = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getOrderSn, orderSn)
                        .eq(OrderInfo::getDeleted, 0)
        );

        if (order == null) {
            log.warn("Order not found: {}", orderSn);
            return;
        }

        // 幂等处理：已支付/已取消的订单不再处理
        if (order.getStatus() != 0) {
            log.info("Order already processed: orderSn={}, status={}", orderSn, order.getStatus());
            return;
        }

        // 释放锁定库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        for (OrderItem item : items) {
            stockMapper.unlockStock(item.getSkuId(), item.getQuantity());
        }

        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason("超时未支付，系统自动取消");
        orderInfoMapper.updateById(order);

        // 秒杀订单（orderType=1）无 OrderItem，上面的 unlockStock 对其无效，
        // 需走秒杀专用回补：Redis 库存 + 已完成标记 + DB stock + 活动剩余库存
        if (order.getOrderType() != null && order.getOrderType() == 1) {
            seckillStockRestoreService.restoreOnCancel(orderSn);
        }

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setOrderSn(orderSn);
        statusLog.setFromStatus(0);
        statusLog.setToStatus(4);
        statusLog.setOperator("system");
        statusLog.setRemark("超时未支付，系统自动取消");
        statusLog.setCreateTime(LocalDateTime.now());
        statusLogMapper.insert(statusLog);

        log.info("Order auto-cancelled due to timeout: {}", orderSn);
    }
}
