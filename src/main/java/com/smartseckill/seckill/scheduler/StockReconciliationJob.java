package com.smartseckill.seckill.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.common.util.SeckillLockUtil;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * Redis-MySQL 库存对账定时任务。
 *
 * <h3>为什么需要对账</h3>
 * <p>
 * 秒杀系统中 Redis 预减库存与 MySQL 真实库存之间存在异步窗口：
 * <ol>
 *   <li>用户请求 → Lua 原子预减 Redis 库存 → 发送 MQ</li>
 *   <li>MQ 消费者 → 创建 DB 订单 → 扣减 MySQL 库存</li>
 * </ol>
 * 中间任何一步失败都可能导致 Redis 与 MySQL 不一致：
 * <ul>
 *   <li><b>Redis 宕机重启</b>：所有预减库存数据丢失 → 超卖</li>
 *   <li><b>MQ 消息丢失</b>：Redis 已扣但订单未创建 → 少卖</li>
 *   <li><b>MQ 消费异常</b>：订单创建失败但 Redis 未回滚 → 少卖</li>
 *   <li><b>网络分区</b>：Redis 或 MQ 部分不可用期间的脏数据</li>
 * </ul>
 * </p>
 *
 * <h3>对账公式（关键：必须扣除在途部分，否则活动进行中会持续超卖）</h3>
 * <pre>
 *   稳态（库存 Key 存在）：
 *     预期 Redis 库存 = totalStock - 有效订单数 - 在途锁数
 *     — 在途锁（seckill:lock:{activityId}:*）与"Lua 已扣 Redis 但订单未落库"一一对应
 *
 *   库存 Key 丢失（Redis 重启/数据丢失，锁也随之丢失）：
 *     预期 Redis 库存 = totalStock - 有效订单数 - MQ 就绪队列深度
 *     — 队列中积压的消息落库时不会再扣 Redis，必须预先扣除
 *
 *   有效订单数：排除 status=4（已取消）——取消后库存已由 SeckillStockRestoreService 回补
 *
 *   差异 = 实际 Redis - 预期 Redis
 *   差异 > 0 → Redis 多扣了，需要回补（MQ 消费失败/延迟）
 *   差异 < 0 → Redis 少扣了，需要扣减（可能 Redis 数据被意外修改）
 *   差异 = 0 → 一致，无需处理
 * </pre>
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li>Redis 库存绝不会被设为负数（clamp 到 0）</li>
 *   <li>只修复进行中(status=1)的活动，已结束/已取消的活动不处理</li>
 *   <li>锁兜底 TTL 60s：消费彻底失败的消息在锁过期后才会被对账回补，避免与在途消息混淆</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciliationJob {

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitAdmin rabbitAdmin;

    /**
     * 每分钟对账一次。
     * 第 23 秒执行，避开整点和半分（与其他定时任务错峰）。
     */
    @Scheduled(cron = "23 * * * * *")  // 每分钟第23秒
    public void reconcile() {
        log.debug("Stock reconciliation started...");

        // 查询所有进行中的秒杀活动
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1)
                        .eq(SeckillActivity::getDeleted, 0)
        );

        if (activities.isEmpty()) {
            return;
        }

        int fixedCount = 0;
        for (SeckillActivity activity : activities) {
            try {
                if (reconcileActivity(activity)) {
                    fixedCount++;
                }
            } catch (Exception e) {
                log.error("Stock reconciliation failed for activityId={}: {}",
                        activity.getId(), e.getMessage());
            }
        }

        if (fixedCount > 0) {
            log.warn("Stock reconciliation: {} activity(s) had stock mismatch and were fixed", fixedCount);
        }
    }

    /**
     * 对账单个活动的库存。
     *
     * @return true=发现并修复了不一致, false=一致无需修复
     */
    private boolean reconcileActivity(SeckillActivity activity) {
        Long activityId = activity.getId();
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;

        // 1. 统计有效订单数（排除已取消 status=4：取消后库存已回补，不能重复扣）
        Long orderCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getActivityId, activityId)
                        .ne(SeckillOrder::getStatus, 4)
        );

        int totalStock = activity.getTotalStock() != null ? activity.getTotalStock() : 0;
        // DB 侧应有剩余库存（不含在途，供 availableStock 校正使用）
        int expectedDbStock = totalStock - orderCount.intValue();
        if (expectedDbStock < 0) {
            // 订单数超过总库存？理论上不应发生（Lua 原子防超卖），但万一发生则 clamp 到 0
            log.error("CRITICAL: orderCount({}) > totalStock({}) for activityId={}! Over-sold detected.",
                    orderCount, totalStock, activityId);
            expectedDbStock = 0;
        }

        // 2. 读取 Redis 实际库存
        String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);
        Integer actualRedisStock = null;
        if (redisStockStr != null) {
            try {
                actualRedisStock = Integer.parseInt(redisStockStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid stock value in Redis: activityId={}, value='{}'", activityId, redisStockStr);
            }
        }

        // 3. 判断并修复
        if (actualRedisStock == null) {
            // 库存 Key 丢失 → 锁也随之丢失，在途表现为 MQ 积压 → 恢复时必须扣除队列深度，
            // 否则积压消息落库时不再扣 Redis → 超卖
            int queueDepth = getSeckillQueueDepth();
            int recoveredStock = Math.max(0, expectedDbStock - queueDepth);
            log.warn("Redis stock key MISSING for active activityId={} (recovered from DB: {} = totalStock({}) - orders({}) - queueDepth({}))",
                    activityId, recoveredStock, totalStock, orderCount, queueDepth);
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(recoveredStock));
            // 同时恢复活动缓存
            recoverActivityCache(activity);
            return true;
        }

        // 4. 稳态：预期 Redis = DB 应有库存 - 在途锁数（在途 = Lua 已扣 Redis 但订单未落库）
        //    不扣在途会在活动进行中把 Redis 库存上提 → 在途部分被重复发放 → 超卖
        int pendingLocks = countPendingLocks(activityId);
        int expectedRedisStock = Math.max(0, expectedDbStock - pendingLocks);

        if (actualRedisStock == expectedRedisStock) {
            // 一致，无需修复
            return false;
        }

        // 不一致 → 修复为预期值
        log.warn("Redis-MySQL stock MISMATCH: activityId={}, redisStock={}, expected={} (totalStock={} - orders={} - pendingLocks={})",
                activityId, actualRedisStock, expectedRedisStock, totalStock, orderCount, pendingLocks);

        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(expectedRedisStock));

        // 同步 MySQL availableStock（以 DB 有效订单数为准，不含在途——消费者落库时自行扣减）
        if (!activity.getAvailableStock().equals(expectedDbStock)) {
            Integer oldAvailable = activity.getAvailableStock();
            activity.setAvailableStock(expectedDbStock);
            activityMapper.updateById(activity);
            log.info("MySQL availableStock corrected: activityId={}, {}→{}",
                    activityId, oldAvailable, expectedDbStock);
        }

        return true;
    }

    /**
     * 统计活动的在途锁数量（SCAN 非阻塞，避免 KEYS 命令）。
     * 锁与"Lua 已扣 Redis、订单未落库"的请求一一对应（锁兜底 TTL 60s）。
     */
    private int countPendingLocks(Long activityId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(SeckillLockUtil.SECKILL_LOCK_PREFIX + activityId + ":*")
                .count(1000)
                .build();
        int count = 0;
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        } catch (Exception e) {
            log.warn("Failed to scan pending locks for activityId={}: {}", activityId, e.getMessage());
        }
        return count;
    }

    /**
     * 获取秒杀订单队列的就绪消息深度（RabbitMQ 管理接口）。
     * MQ 不可用时返回 0 并告警——恢复值会偏高（少卖方向，安全），队列排空后下一轮对账自愈。
     */
    private int getSeckillQueueDepth() {
        try {
            Properties props = rabbitAdmin.getQueueProperties(RabbitMQConfig.SECKILL_ORDER_QUEUE);
            if (props != null) {
                Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                return count != null ? Integer.parseInt(count.toString()) : 0;
            }
        } catch (Exception e) {
            log.warn("Failed to query seckill queue depth (treated as 0): {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 恢复活动 Redis 缓存（预热数据丢失时重建）。
     */
    private void recoverActivityCache(SeckillActivity activity) {
        String activityKey = RedisUtil.SECKILL_ACTIVITY_PREFIX + activity.getId();
        try {
            stringRedisTemplate.opsForHash().put(activityKey, "skuId", String.valueOf(activity.getSkuId()));
            stringRedisTemplate.opsForHash().put(activityKey, "spuId", String.valueOf(activity.getSpuId()));
            stringRedisTemplate.opsForHash().put(activityKey, "seckillPrice", activity.getSeckillPrice().toString());
            stringRedisTemplate.opsForHash().put(activityKey, "limitPerUser",
                    String.valueOf(activity.getLimitPerUser() != null ? activity.getLimitPerUser() : 1));
            stringRedisTemplate.opsForHash().put(activityKey, "status", String.valueOf(activity.getStatus()));
            stringRedisTemplate.opsForHash().put(activityKey, "startTime",
                    activity.getStartTime() != null ? activity.getStartTime().toString() : "");
            stringRedisTemplate.opsForHash().put(activityKey, "endTime",
                    activity.getEndTime() != null ? activity.getEndTime().toString() : "");
            log.info("Activity cache recovered for activityId={}", activity.getId());
        } catch (Exception e) {
            log.error("Failed to recover activity cache: activityId={}, error={}",
                    activity.getId(), e.getMessage());
        }
    }

    // ==================== 活动结束清理 ====================

    /**
     * 活动结束后清理 Redis 缓存（每 5 分钟执行）。
     * <p>
     * 清理内容：
     * <ul>
     *   <li>秒杀库存 Key（seckill:stock:*）</li>
     *   <li>在线人数 ZSet（seckill:online:*）</li>
     *   <li>秒杀锁 Key（通过 TTL 自动过期，不需要主动清理）</li>
     * </ul>
     * 活动缓存 Hash（seckill:activity:*）保留至第二天，供前端查看已结束活动。
     * </p>
     */
    @Scheduled(cron = "43 */5 * * * *")  // 每5分钟第43秒
    public void cleanupEndedActivities() {
        List<SeckillActivity> ended = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 2)  // 已结束
                        .eq(SeckillActivity::getDeleted, 0)
        );

        for (SeckillActivity activity : ended) {
            Long activityId = activity.getId();
            // 清理库存 Key
            stringRedisTemplate.delete(RedisUtil.SECKILL_STOCK_PREFIX + activityId);
            // 清理在线人数 Key
            stringRedisTemplate.delete(RedisUtil.SECKILL_ONLINE_PREFIX + activityId);
        }

        if (!ended.isEmpty()) {
            log.info("Cleaned up Redis keys for {} ended activities", ended.size());
        }
    }
}
