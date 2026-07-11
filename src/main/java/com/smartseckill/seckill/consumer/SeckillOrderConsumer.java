package com.smartseckill.seckill.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.product.entity.ProductSku;
import com.smartseckill.product.mapper.ProductSkuMapper;
import com.smartseckill.product.mapper.ProductStockMapper;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import com.smartseckill.seckill.message.SeckillOrderMessage;
import com.smartseckill.seckill.sse.SeckillEventPublisher;
import com.smartseckill.common.util.SeckillLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单异步消费者。
 * <p>
 * 从 RabbitMQ 消费秒杀订单消息，完成数据库层面的订单创建和库存扣减。
 * 由于 Redis 层已经做了库存过滤，进入此消费者的消息量 ≤ 实际库存量，
 * 数据库压力可控（通常每秒几百到几千条）。
 * </p>
 * <p>
 * 消费端配置（application.yml）：
 * <ul>
 *   <li>prefetch=10：一次拉取 10 条消息，平衡吞吐和内存</li>
 *   <li>concurrency=10：10 个并发消费者线程</li>
 *   <li>max-concurrency=30：高峰期最多扩展到 30 个线程</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivityMapper activityMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final ProductStockMapper stockMapper;
    private final ProductSkuMapper skuMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillEventPublisher eventPublisher;
    private final SysUserMapper sysUserMapper;
    private final SeckillLockUtil seckillLockUtil;

    /**
     * 消费秒杀订单创建消息。
     * <p>
     * 幂等性：通过 seckill_order 表的 (activity_id, user_id) 唯一索引保证。
     * 如果消息重复消费（网络重试），DuplicateKeyException 会被捕获并忽略。
     * </p>
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE,
                   concurrency = "10-30")
    @Transactional
    public void handleSeckillOrder(SeckillOrderMessage message) {
        log.info("Processing seckill order: activityId={}, userId={}", message.getActivityId(), message.getUserId());

        Long activityId = message.getActivityId();
        Long userId = message.getUserId();
        Long skuId = message.getSkuId();

        try {
            // 1. 创建订单主表（秒杀订单走普通订单表，orderType=1）
            String orderSn = generateOrderSn();
            ProductSku sku = skuMapper.selectById(skuId);
            if (sku == null) {
                log.error("SKU not found: skuId={}", skuId);
                // SKU 不存在：释放锁，避免用户永久阻塞
                seckillLockUtil.releaseLock(activityId, userId);
                return;
            }

            // 计算金额
            BigDecimal totalAmount = message.getSeckillPrice().multiply(
                    BigDecimal.valueOf(message.getQuantity()));

            OrderInfo order = new OrderInfo();
            order.setOrderSn(orderSn);
            order.setUserId(userId);
            order.setTotalAmount(totalAmount);
            order.setPayAmount(totalAmount);
            order.setFreightAmount(BigDecimal.ZERO);
            order.setStatus(0); // 待支付
            order.setOrderType(1); // 秒杀订单
            orderInfoMapper.insert(order);

            // 2. 创建秒杀订单记录（activity_id + user_id 唯一索引防重）
            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setActivityId(activityId);
            seckillOrder.setUserId(userId);
            seckillOrder.setOrderId(order.getId());
            seckillOrder.setOrderSn(orderSn);
            seckillOrder.setSkuId(skuId);
            seckillOrder.setSeckillPrice(message.getSeckillPrice());
            seckillOrder.setQuantity(message.getQuantity());
            seckillOrder.setStatus(0); // 待支付
            seckillOrderMapper.insert(seckillOrder);

            // 3. 扣减数据库真实库存（Redis 已预减，这里做最终扣减）
            int deductResult = stockMapper.deductStock(skuId, message.getQuantity());
            if (deductResult == 0) {
                log.error("Stock deduct failed for seckill order: orderSn={}, skuId={}", orderSn, skuId);
                // 库存扣减失败但已预减过 Redis，记录异常由人工/定时任务处理
            }

            // 4. 更新活动剩余库存
            SeckillActivity activity = activityMapper.selectById(activityId);
            if (activity != null) {
                activity.setAvailableStock(activity.getAvailableStock() - message.getQuantity());
                activityMapper.updateById(activity);
            }

            log.info("Seckill order created: orderSn={}, userId={}, activityId={}", orderSn, userId, activityId);

            // 4.5. 写入已完成标记，阻止用户重复秒杀同一活动
            //     标记在 Lua 原子检查中与锁/库存一起校验，完全消除锁释放后的时间窗口
            seckillLockUtil.markCompleted(activityId, userId);

            // 5. 释放秒杀锁（订单创建成功，允许用户再次参与其他秒杀活动）
            seckillLockUtil.releaseLock(activityId, userId);

            // 推送秒杀成功事件（中奖公告 + 个人结果）
            eventPublisher.publishSeckillResult(activityId, userId, "success",
                    "恭喜！秒杀成功", orderSn);
            // 异步获取用户名并推送中奖公告
            publishWinnerAsync(activityId, userId);

        } catch (DuplicateKeyException e) {
            // 幂等：重复消费（activity_id + user_id 唯一索引冲突），直接忽略
            // 原订单已创建成功，确保已完成标记存在，然后释放锁
            log.warn("Duplicate seckill order ignored: activityId={}, userId={}", activityId, userId);
            seckillLockUtil.markCompleted(activityId, userId);
            seckillLockUtil.releaseLock(activityId, userId);
        } catch (Exception e) {
            log.error("Failed to create seckill order: activityId={}, userId={}", activityId, userId, e);
            // 异常会被 Spring AMQP 捕获并重试（配置了 retry: max-attempts=3）
            throw e;
        }
    }

    private String generateOrderSn() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String seqKey = "order:seq:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
        if (seq != null && seq == 1L) {
            stringRedisTemplate.expire(seqKey, 1, TimeUnit.DAYS);
        }
        return "SK" + datePart + String.format("%06d", seq != null ? seq % 1000000 : 1);
    }

    /** 异步发布中奖公告（查询用户名后脱敏发送） */
    private void publishWinnerAsync(Long activityId, Long userId) {
        try {
            SysUser user = sysUserMapper.selectById(userId);
            String username = user != null ? user.getUsername() : "用户" + userId;
            eventPublisher.publishWinnerAnnouncement(activityId, username);
        } catch (Exception e) {
            log.debug("Failed to publish winner announcement: userId={}, error={}", userId, e.getMessage());
        }
    }
}
