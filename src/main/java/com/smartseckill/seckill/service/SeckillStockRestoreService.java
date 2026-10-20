package com.smartseckill.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.common.util.SeckillLockUtil;
import com.smartseckill.product.mapper.ProductStockMapper;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 秒杀订单取消/超时关单后的库存回补服务。
 *
 * <h3>为什么需要独立的回补逻辑</h3>
 * <p>
 * 普通订单取消走 {@code unlockStock}（释放 lock_stock），但秒杀链路不经过 lock 阶段
 * （Redis Lua 预扣 → 消费者直接扣 stock），且秒杀订单没有 OrderItem 记录，
 * 因此普通取消逻辑对秒杀订单完全无感——库存、Redis 预扣、防重标记全部漏回补，
 * 造成库存永久漏损（少卖）且用户无法重新参与。
 * </p>
 *
 * <h3>回补内容（与扣减路径一一对应）</h3>
 * <ol>
 *   <li>seckill_order.status → 4（已取消），作为幂等/并发守卫</li>
 *   <li>Redis 库存 INCRBY 回补（仅当库存 Key 仍存在，活动结束清理后不重建）</li>
 *   <li>删除已完成标记，允许用户重新参与该活动</li>
 *   <li>DB product_stock 回补 stock（不动 lock_stock）</li>
 *   <li>seckill_activity.available_stock 回补</li>
 * </ol>
 *
 * <h3>与对账任务的约定</h3>
 * <p>
 * {@code StockReconciliationJob} 统计订单数时排除 status=4（已取消），
 * 因此回补不会被下一轮对账重新扣掉。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockRestoreService {

    /** 仅当库存 Key 存在时才回补：活动已结束/缓存已清理时禁止重建 Key，防止产生幻影库存 */
    private static final String RESTORE_STOCK_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
            "    return redis.call('INCRBY', KEYS[1], tonumber(ARGV[1])) " +
            "end " +
            "return -1";

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivityMapper activityMapper;
    private final ProductStockMapper stockMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 回补秒杀订单占用的库存（用户取消 / 超时关单时调用）。
     * <p>
     * 幂等：以 seckill_order.status 条件更新作为并发守卫，
     * 用户取消与超时关单并发触发时仅一个生效。调用方需持有事务（本方法随调用方事务回滚）。
     * </p>
     *
     * @param orderSn 订单号
     */
    public void restoreOnCancel(String orderSn) {
        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderSn, orderSn));
        if (seckillOrder == null) {
            log.warn("Seckill order not found for restore: orderSn={}", orderSn);
            return;
        }

        // 并发守卫：仅第一次取消生效
        int updated = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getId, seckillOrder.getId())
                .ne(SeckillOrder::getStatus, 4)
                .set(SeckillOrder::getStatus, 4));
        if (updated == 0) {
            log.info("Seckill order already restored, skip: orderSn={}", orderSn);
            return;
        }

        Long activityId = seckillOrder.getActivityId();
        Long userId = seckillOrder.getUserId();
        Integer quantity = seckillOrder.getQuantity() != null ? seckillOrder.getQuantity() : 1;

        // 1. 回补 Redis 库存（仅当 Key 仍存在）
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESTORE_STOCK_LUA, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(stockKey), String.valueOf(quantity));
        if (result != null && result >= 0) {
            log.info("Redis stock restored: activityId={}, quantity={}, newStock={}", activityId, quantity, result);
        } else {
            log.info("Redis stock key gone (activity ended), skip Redis restore: activityId={}", activityId);
        }

        // 2. 删除已完成标记，允许用户重新参与该活动
        stringRedisTemplate.delete(SeckillLockUtil.SECKILL_COMPLETED_PREFIX + activityId + ":" + userId);

        // 3. 回补 DB 真实库存（只加 stock，不动 lock_stock）
        stockMapper.restoreSeckillStock(seckillOrder.getSkuId(), quantity);

        // 4. 回补活动剩余库存
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity != null) {
            activity.setAvailableStock(activity.getAvailableStock() + quantity);
            activityMapper.updateById(activity);
        }

        log.info("Seckill order restored on cancel: orderSn={}, activityId={}, userId={}, quantity={}",
                orderSn, activityId, userId, quantity);
    }
}
