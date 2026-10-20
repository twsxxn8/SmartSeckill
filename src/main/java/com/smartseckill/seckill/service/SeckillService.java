package com.smartseckill.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.cache.BloomFilterService;
import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.common.util.SeckillLockUtil;
import com.smartseckill.common.util.SeckillTokenUtil;
import com.smartseckill.product.entity.ProductSku;
import com.smartseckill.product.mapper.ProductSkuMapper;
import com.smartseckill.product.mapper.ProductStockMapper;
import com.smartseckill.seckill.dto.SeckillRequest;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.message.SeckillOrderMessage;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.seckill.sse.SeckillEventPublisher;
import com.smartseckill.user.mapper.SysUserMapper;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.risk.service.RiskControlService;
import com.smartseckill.risk.model.RiskDecision;
import com.smartseckill.risk.model.RiskDecision.RiskAction;
import lombok.RequiredArgsConstructor;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 秒杀核心业务服务。
 * <p>
 * 完整秒杀链路（高并发热路径）：
 * <ol>
 *   <li><b>预热</b>：管理员通过 {@link #preWarmStock(Long)} 将活动库存加载到 Redis</li>
 *   <li><b>令牌</b>：用户通过 {@link SeckillTokenUtil#generateToken} 获取防重 Token</li>
 *   <li><b>限流</b>：Controller 层 {@code @RateLimit} 双层限流</li>
 *   <li><b>风控</b>：AI 风控模型评估用户行为，可拦截/验证/延迟</li>
 *   <li><b>加锁</b>：{@link SeckillLockUtil#acquireLock} Lua 原子操作：
 *       校验Token + 防重检查 + 库存预减 + 加锁（一条脚本原子完成，消除竞态窗口）</li>
 *   <li><b>异步</b>：发送 RabbitMQ 消息，削峰填谷，消费者异步创建数据库订单</li>
 *   <li><b>释放锁</b>：订单创建成功后释放锁（消费者或同步兜底路径）</li>
 * </ol>
 * </p>
 * <p>
 * <b>为什么这样设计：</b>
 * 秒杀的核心瓶颈在数据库——行锁竞争导致并发极低。将库存校验前置到 Redis（单机 10w+ QPS），
 * 只有成功预减的请求才进入 MQ，MQ 消费者以数据库能承受的速度（~1k TPS）串行/小并发创建订单。
 * 这样即使 100 万人同时秒杀，也只有 N（库存量）条消息进入 MQ，数据库压力可控。
 * </p>
 * <p>
 * <b>锁方案优势：</b>
 * 传统方案中 Token 校验、用户防重、库存扣减是 3 个独立 Redis 调用，
 * 之间存在竞态窗口——同一用户的并发请求可能在 Token 校验后、Set 标记前同时通过。
 * 新的 Lua 原子锁方案将 4 步合并为 1 步，在 Redis 单线程中一气呵成，彻底消除竞态条件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillTokenUtil seckillTokenUtil;
    private final SeckillLockUtil seckillLockUtil;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillActivityMapper activityMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductStockMapper stockMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillEventPublisher eventPublisher;
    private final RiskControlService riskControlService;
    private final HttpServletRequest httpServletRequest;
    private final OrderInfoMapper orderInfoMapper;
    private final SysUserMapper sysUserMapper;
    private final BloomFilterService bloomFilterService;

    /**
     * 活动预热：将秒杀活动信息和库存数量加载到 Redis。
     * <p>
     * 在活动开始前由管理员调用，也可由定时任务自动预热。
     * 预热后的数据格式：
     * <ul>
     *   <li>{@code seckill:stock:{activityId}} → 库存数量（String，供 Lua DECR 原子扣减）</li>
     *   <li>{@code seckill:activity:{activityId}} → 活动 SKU/价格等关键信息（Hash）</li>
     * </ul>
     * </p>
     */
    @Transactional(readOnly = true)
    public void preWarmStock(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        if (activity.getStatus() != 0 && activity.getStatus() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能预热未开始或进行中的活动");
        }
        if (activity.getSkuId() == null || activity.getSpuId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动缺少SKU/SPU信息，请先完善活动数据");
        }
        if (activity.getSeckillPrice() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动缺少秒杀价格，请先完善活动数据");
        }

        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String activityKey = RedisUtil.SECKILL_ACTIVITY_PREFIX + activityId;

        int stock = activity.getAvailableStock() != null ? activity.getAvailableStock() : 0;
        int limitPerUser = activity.getLimitPerUser() != null ? activity.getLimitPerUser() : 1;

        // 将库存写入 Redis（用于 Lua 原子扣减）
        // 注意：库存 Key 不设 TTL —— 活动期间必须始终存在，
        // TTL 到期会导致 Lua 脚本返回 -3（库存 Key 不存在）= 抢光，后果严重。
        // 活动结束后由 StockReconciliationJob.cleanupEndedActivities() 清理。
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));

        // 将活动 SKU/价格信息写入 Redis Hash（消费者创建订单时使用）
        stringRedisTemplate.opsForHash().put(activityKey, "skuId", String.valueOf(activity.getSkuId()));
        stringRedisTemplate.opsForHash().put(activityKey, "spuId", String.valueOf(activity.getSpuId()));
        stringRedisTemplate.opsForHash().put(activityKey, "seckillPrice", activity.getSeckillPrice().toString());
        stringRedisTemplate.opsForHash().put(activityKey, "limitPerUser", String.valueOf(limitPerUser));
        // 同时缓存状态和时间字段，供 checkActivity() Redis 路径校验
        stringRedisTemplate.opsForHash().put(activityKey, "status", String.valueOf(activity.getStatus()));
        stringRedisTemplate.opsForHash().put(activityKey, "startTime",
                activity.getStartTime() != null ? activity.getStartTime().toString() : "");
        stringRedisTemplate.opsForHash().put(activityKey, "endTime",
                activity.getEndTime() != null ? activity.getEndTime().toString() : "");
        // Hash Key 设 24h TTL（活动结束后自动清理，checkActivity 可在 TTL 到期后回退 DB）
        stringRedisTemplate.expire(activityKey, 24, java.util.concurrent.TimeUnit.HOURS);

        log.info("Seckill pre-warmed: activityId={}, stock={}, skuId={}",
                activityId, stock, activity.getSkuId());
    }

    /**
     * 执行秒杀（热路径，需极高性能）。
     * <p>
     * 此方法在以下检查全部通过后调用（Controller + AOP 已完成限流）：
     * <ol>
     *   <li>风控评估 → AI 风控模型</li>
     *   <li>原子获取秒杀锁 → {@link SeckillLockUtil#acquireLock}
     *       （Token 校验 + 防重检查 + 库存预减 + 加锁，单条 Lua 脚本原子完成）</li>
     * </ol>
     * 全部通过后发送 MQ 消息，返回"排队中"。
     * 锁在 MQ 消费者/同步兜底创建订单成功后释放。
     * </p>
     *
     * @param userId  用户 ID
     * @param request 秒杀请求（activityId + token）
     */
    public void executeSeckill(Long userId, SeckillRequest request) {
        Long activityId = request.getActivityId();

        // 1. 校验活动是否在进行中（从 Redis 快速检查，兜底查 DB）
        SeckillActivity activity = checkActivity(activityId);

        // 2. AI 风控评估（加锁前，不浪费 Redis 原子操作）
        RiskDecision riskDecision = riskControlService.evaluateRisk(
                userId, activityId, getCurrentIp(), getCurrentUserAgent());
        if (riskDecision.getAction() == RiskAction.BLOCK) {
            log.warn("Seckill blocked by risk control: userId={}, activityId={}, score={}, reason={}",
                    userId, activityId, riskDecision.getRiskScore(), riskDecision.getReason());
            eventPublisher.publishSeckillResult(activityId, userId, "blocked",
                    "系统检测到异常操作，请稍后再试", null);
            throw new BusinessException(ResultCode.RISK_CONTROL_BLOCKED);
        }
        if (riskDecision.getAction() == RiskAction.VERIFY) {
            throw new BusinessException(ResultCode.RISK_CONTROL_VERIFY);
        }
        if (riskDecision.getAction() == RiskAction.DELAY) {
            // 添加随机延迟（100-500ms）以缓解异常请求
            try {
                Thread.sleep(100 + (long) (Math.random() * 400));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 3. 原子获取秒杀锁（Token校验 + 防重 + 库存预减 + 加锁，一条 Lua 原子完成）
        long lockResult = seckillLockUtil.acquireLock(activityId, userId, request.getToken());

        if (lockResult == SeckillLockUtil.ACQUIRE_FAIL_TOKEN_INVALID) {
            // Token 无效或已被使用（重复提交）
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }
        if (lockResult == SeckillLockUtil.ACQUIRE_FAIL_ALREADY_LOCKED) {
            // 用户已持有锁（重复点击）
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }
        if (lockResult == SeckillLockUtil.ACQUIRE_FAIL_SOLD_OUT
                || lockResult == SeckillLockUtil.ACQUIRE_FAIL_NO_STOCK_KEY) {
            log.info("Seckill sold out: activityId={}, userId={}", activityId, userId);
            eventPublisher.publishSeckillResult(activityId, userId, "failed", "已抢光", null);
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }
        if (lockResult == SeckillLockUtil.ACQUIRE_FAIL_ALREADY_COMPLETED) {
            // 用户已经秒杀成功过 → 拒绝
            log.info("Seckill duplicate purchase blocked: activityId={}, userId={}", activityId, userId);
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }

        // ========== 锁已获取，以下为订单创建逻辑 ==========

        // 4. 更新在线竞争计数（Redis ZSet，score=当前时间戳）
        updateOnlineCount(activityId, userId);

        // 5. 推送事件：库存变化 + 竞争人数更新
        eventPublisher.publishStockChanged(activityId);
        int onlineCount = getOnlineCompetitorCount(activityId);
        eventPublisher.publishCompetitorJoined(activityId, onlineCount);

        // 6. MQ 发送 + 兜底同步创建订单
        // 双重机制确保订单一定会被创建：
        //   - 优先：MQ 异步（削峰填谷，消费者 SeckillOrderConsumer 并发创建订单）
        //   - 兜底：MQ 不可用时同步创建（直接写 DB，保证用户不会白抢）
        SeckillOrderMessage message = new SeckillOrderMessage(
                activityId,
                userId,
                activity.getSkuId(),
                activity.getSpuId(),
                activity.getSeckillPrice(),
                1  // 秒杀每次限购 1 件
        );
        boolean mqSent = false;
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    message
            );
            mqSent = true;
            log.debug("MQ message sent: activityId={}, userId={}", activityId, userId);
        } catch (Exception e) {
            log.warn("RabbitMQ unavailable, creating order directly: activityId={}, userId={}",
                    activityId, userId);
        }

        if (!mqSent) {
            // 兜底：直接同步创建订单（MQ 不可用时）
            try {
                createSeckillOrderSync(message);
            } finally {
                // 无论订单创建成功与否，都释放锁
                // 如果订单创建失败，释放锁让用户可以重试
                seckillLockUtil.releaseLock(activityId, userId);
            }
        }
        // MQ 路径：锁由消费者在创建订单成功后释放（见 SeckillOrderConsumer）

        log.info("Seckill request accepted: activityId={}, userId={}", activityId, userId);
        // 不抛异常 = 进入排队，前端根据返回的 success 显示"排队中"
    }

    /**
     * 查询秒杀结果。
     * <p>
     * 返回三种状态：
     * <ul>
     *   <li>{@code success} — 已抢到，附带订单号（锁已释放 + 订单已生成）</li>
     *   <li>{@code queuing} — 持有锁，等待 MQ 消费者处理</li>
     *   <li>{@code failed} — 未参与或未抢到（无锁）</li>
     * </ul>
     * </p>
     *
     * @param userId     用户 ID
     * @param activityId 秒杀活动 ID
     * @return 包含 status、orderSn 等字段的 Map
     */
    public java.util.Map<String, Object> getSeckillResult(Long userId, Long activityId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("activityId", activityId);

        // 1. 先查询秒杀订单表（订单已生成 = 秒杀成功）
        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getActivityId, activityId)
                        .eq(SeckillOrder::getUserId, userId)
        );

        if (seckillOrder != null) {
            // 已生成订单 → 秒杀成功
            result.put("status", "success");
            result.put("message", "恭喜！秒杀成功");
            result.put("orderSn", seckillOrder.getOrderSn());
            result.put("orderId", seckillOrder.getOrderId());
            result.put("seckillPrice", seckillOrder.getSeckillPrice());
            return result;
        }

        // 2. 已完成标记存在但订单还未生成 = 订单创建中（介于锁释放和 DB 落库之间）
        if (seckillLockUtil.isCompleted(activityId, userId)) {
            result.put("status", "queuing");
            result.put("message", "订单生成中，请稍候...");
            return result;
        }

        // 3. 无订单 + 持有锁 = 排队中
        if (seckillLockUtil.isLocked(activityId, userId)) {
            result.put("status", "queuing");
            result.put("message", "排队中，订单正在生成...");
            return result;
        }

        // 4. 无订单 + 无锁 + 无完成标记 = 未参与
        result.put("status", "none");
        result.put("message", "未参与该秒杀活动");
        return result;
    }

    /**
     * 获取当前秒杀活动的在线竞争人数。
     */
    public int getOnlineCompetitorCount(Long activityId) {
        String onlineKey = RedisUtil.SECKILL_ONLINE_PREFIX + activityId;
        // 清理过期用户（超过 ONLINE_TTL 秒未活跃的）
        long cutoff = System.currentTimeMillis() - RedisUtil.SECKILL_ONLINE_TTL_SECONDS * 1000;
        stringRedisTemplate.opsForZSet().removeRangeByScore(onlineKey, 0, cutoff);
        Long size = stringRedisTemplate.opsForZSet().size(onlineKey);
        return size != null ? size.intValue() : 0;
    }

    // ==================== 私有方法 ====================

    /** 更新在线竞争计数：将用户加入 ZSet，score=当前时间戳 */
    private void updateOnlineCount(Long activityId, Long userId) {
        String onlineKey = RedisUtil.SECKILL_ONLINE_PREFIX + activityId;
        double now = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().add(onlineKey, String.valueOf(userId), now);
        // 设置 Key 过期时间（2 倍 TTL，防止僵尸 Key）
        stringRedisTemplate.expire(onlineKey,
                RedisUtil.SECKILL_ONLINE_TTL_SECONDS * 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * 生成秒杀防重 Token（秒杀前调用）。
     * 前端先拿 Token，再带 Token 调秒杀接口。
     */
    public String generateSeckillToken(Long userId, Long activityId) {
        // 只校验活动存在，不校验状态和时间
        // 原因：未开始的活动也需要允许用户获取 Token，否则用户无法在活动开始前做好准备
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        // 已秒杀成功的用户拒绝发放新 Token（双重拦截：此处 + Lua 原子检查）
        if (seckillLockUtil.isCompleted(activityId, userId)) {
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }
        return seckillTokenUtil.generateToken(userId, activityId);
    }

    // ==================== 活动管理 ====================

    /** 获取进行中或即将开始的秒杀活动列表 */
    public java.util.List<SeckillActivity> getActiveList() {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getDeleted, 0)
                        .isNotNull(SeckillActivity::getActivityName)
                        .and(w -> w.in(SeckillActivity::getStatus, 0, 1))
                        .gt(SeckillActivity::getEndTime, now)  // 排除已结束的活动，防止 status 未及时更新
                        .orderByAsc(SeckillActivity::getStartTime)
        );
    }

    /** 获取秒杀活动详情（前端页面展示用，附带活动是否可参与判断） */
    public SeckillActivity getActivityDetail(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        // 优先用 Redis 实时库存覆盖 DB 值，避免异步订单创建导致 DB 滞后
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String redisStock = stringRedisTemplate.opsForValue().get(stockKey);
        if (redisStock != null) {
            try {
                int stock = Integer.parseInt(redisStock);
                activity.setAvailableStock(Math.max(0, stock));
            } catch (NumberFormatException ignored) { /* 格式异常则保留 DB 值 */ }
        }
        // 基于当前时间修正状态，防止 DB status 字段未及时更新导致前端显示不一致
        // 已取消(status=3)的活动保持原状态，不对已取消的活动做自动纠正
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStatus() != null && activity.getStatus() != 3) {
            if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
                activity.setStatus(2);  // 已结束
            } else if (activity.getStartTime() != null && now.isAfter(activity.getStartTime())
                    && activity.getStatus() == 0) {
                activity.setStatus(1);  // 未开始 → 进行中
            }
        }
        return activity;
    }

    @Transactional
    public void createActivity(SeckillActivity activity) {
        // 校验 SKU 存在
        ProductSku sku = skuMapper.selectById(activity.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        activity.setSpuId(sku.getSpuId());
        activity.setOriginalPrice(sku.getPrice());
        activity.setAvailableStock(activity.getTotalStock());
        // 初始状态固定为 0（未开始），前端即使传了其他值也强制覆盖
        activity.setStatus(0);
        activityMapper.insert(activity);
        // 新活动 ID 加入布隆过滤器
        bloomFilterService.addActivity(activity.getId());
    }

    @Transactional
    public void updateActivity(Long id, SeckillActivity activity) {
        SeckillActivity existing = activityMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        // 只更新非 null 的字段，保留原有字段值
        Integer oldTotalStock = existing.getTotalStock();
        boolean stockChanged = activity.getTotalStock() != null
                && !activity.getTotalStock().equals(existing.getTotalStock());
        boolean statusChanged = activity.getStatus() != null
                && !activity.getStatus().equals(existing.getStatus());

        if (activity.getActivityName() != null) existing.setActivityName(activity.getActivityName());
        if (activity.getSkuId() != null) existing.setSkuId(activity.getSkuId());
        if (activity.getSpuId() != null) existing.setSpuId(activity.getSpuId());
        if (activity.getSeckillPrice() != null) existing.setSeckillPrice(activity.getSeckillPrice());
        if (activity.getOriginalPrice() != null) existing.setOriginalPrice(activity.getOriginalPrice());
        if (activity.getTotalStock() != null) existing.setTotalStock(activity.getTotalStock());
        if (activity.getAvailableStock() != null) existing.setAvailableStock(activity.getAvailableStock());
        if (activity.getLimitPerUser() != null) existing.setLimitPerUser(activity.getLimitPerUser());
        if (activity.getStartTime() != null) existing.setStartTime(activity.getStartTime());
        if (activity.getEndTime() != null) existing.setEndTime(activity.getEndTime());
        if (activity.getStatus() != null) existing.setStatus(activity.getStatus());
        activityMapper.updateById(existing);

        String activityKey = RedisUtil.SECKILL_ACTIVITY_PREFIX + id;
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + id;

        // 状态变为已结束/已取消 → 清理 Redis 中的所有活动缓存
        if (statusChanged && (existing.getStatus() == 2 || existing.getStatus() == 3)) {
            stringRedisTemplate.delete(activityKey);
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(RedisUtil.SECKILL_ONLINE_PREFIX + id);
            // 清理秒杀锁和完成标记（pattern 删除）
            var lockKeys = stringRedisTemplate.keys(SeckillLockUtil.SECKILL_LOCK_PREFIX + id + ":*");
            if (lockKeys != null && !lockKeys.isEmpty()) stringRedisTemplate.delete(lockKeys);
            var completedKeys = stringRedisTemplate.keys(SeckillLockUtil.SECKILL_COMPLETED_PREFIX + id + ":*");
            if (completedKeys != null && !completedKeys.isEmpty()) stringRedisTemplate.delete(completedKeys);
            log.info("Seckill activity {} cache cleaned: status changed to {}", id, existing.getStatus());
            return;
        }

        // 库存变动 → 按 delta 原子调整 Redis 库存（仅已预热时）
        // 不能用 SET 覆盖新总库存：会抹掉已产生的订单扣减和 MQ 在途扣减 → 超卖
        if (stockChanged && (existing.getStatus() == 0 || existing.getStatus() == 1)) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
                long delta = (long) existing.getTotalStock() - oldTotalStock;
                stringRedisTemplate.opsForValue().increment(stockKey, delta);
                log.info("Seckill activity {} stock adjusted in Redis: totalStock {} → {} (delta={})",
                        id, oldTotalStock, existing.getTotalStock(), delta);
            } else {
                log.info("Seckill activity {} stock changed in DB only (not pre-warmed in Redis): {} → {}",
                        id, oldTotalStock, existing.getTotalStock());
            }
        }

        // 同步更新 Redis Hash 中的活动信息（已预热时才更新，未预热则跳过）
        String cachedSkuId = (String) stringRedisTemplate.opsForHash().get(activityKey, "skuId");
        if (cachedSkuId != null) {
            if (activity.getStatus() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "status", String.valueOf(existing.getStatus()));
            if (activity.getStartTime() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "startTime",
                        existing.getStartTime() != null ? existing.getStartTime().toString() : "");
            if (activity.getEndTime() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "endTime",
                        existing.getEndTime() != null ? existing.getEndTime().toString() : "");
            if (activity.getSkuId() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "skuId", String.valueOf(existing.getSkuId()));
            if (activity.getSeckillPrice() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "seckillPrice", existing.getSeckillPrice().toString());
            if (activity.getLimitPerUser() != null)
                stringRedisTemplate.opsForHash().put(activityKey, "limitPerUser", String.valueOf(existing.getLimitPerUser()));
        }
    }

    @Transactional
    public void deleteActivity(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        activityMapper.deleteById(id);

        // 清理该活动在 Redis 中的所有缓存
        stringRedisTemplate.delete(RedisUtil.SECKILL_ACTIVITY_PREFIX + id);
        stringRedisTemplate.delete(RedisUtil.SECKILL_STOCK_PREFIX + id);
        stringRedisTemplate.delete(RedisUtil.SECKILL_ONLINE_PREFIX + id);
        stringRedisTemplate.delete(RedisUtil.SECKILL_WINNER_PREFIX + id);
    }

    /** 获取全部秒杀活动（管理员），包含已结束/已取消 */
    public java.util.List<SeckillActivity> getAllActivities() {
        return activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getDeleted, 0)
                        .orderByDesc(SeckillActivity::getCreateTime)
        );
    }

    /**
     * 校验活动有效性：从 Redis 快速检查，兜底查 DB。
     * 返回活动实体（优先从 Redis 组装轻量信息）。
     */
    private SeckillActivity checkActivity(Long activityId) {
        // 0. 布隆预判：绝对不存在 → 快速拒绝
        if (!bloomFilterService.mightContainActivity(activityId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        String activityKey = RedisUtil.SECKILL_ACTIVITY_PREFIX + activityId;
        String skuIdStr = (String) stringRedisTemplate.opsForHash().get(activityKey, "skuId");

        // Redis 命中 → 活动已预热，快速返回轻量对象（但仍需校验状态和时间）
        if (skuIdStr != null) {
            // 从 Redis Hash 读取状态和时间字段（必须校验，防止过期活动 Redis 缓存未清除）
            String statusStr = (String) stringRedisTemplate.opsForHash().get(activityKey, "status");
            String startTimeStr = (String) stringRedisTemplate.opsForHash().get(activityKey, "startTime");
            String endTimeStr = (String) stringRedisTemplate.opsForHash().get(activityKey, "endTime");

            LocalDateTime now = LocalDateTime.now();
            // 时间优先：先检查时间维度（startTime/endTime 比 status 更准确、更新更及时）
            // status 作为兜底：仅在时间字段缺失时使用
            boolean timeChecked = false;
            if (endTimeStr != null && !endTimeStr.isEmpty()) {
                try {
                    if (now.isAfter(LocalDateTime.parse(endTimeStr))) {
                        throw new BusinessException(ResultCode.SECKILL_ALREADY_ENDED);
                    }
                    timeChecked = true;
                } catch (Exception ignored) { /* 格式异常则跳过 */ }
            }
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                try {
                    if (now.isBefore(LocalDateTime.parse(startTimeStr))) {
                        throw new BusinessException(ResultCode.SECKILL_NOT_STARTED);
                    }
                    timeChecked = true;
                } catch (Exception ignored) { /* 格式异常则跳过 */ }
            }
            // 兜底：时间字段缺失或解析失败时，用 status 判断（防止无时间信息的脏数据）
            if (!timeChecked && statusStr != null && !statusStr.isEmpty()) {
                switch (statusStr) {
                    case "0":   // status=0 表示未开始
                        throw new BusinessException(ResultCode.SECKILL_NOT_STARTED);
                    case "1":   // status=1 表示进行中，放行
                        break;
                    default:    // status=2 已结束 / 3 已取消
                        throw new BusinessException(ResultCode.SECKILL_ALREADY_ENDED);
                }
            }

            SeckillActivity activity = new SeckillActivity();
            activity.setId(activityId);
            activity.setSkuId(Long.valueOf(skuIdStr));
            activity.setSpuId(Long.valueOf((String) stringRedisTemplate.opsForHash()
                    .get(activityKey, "spuId")));
            activity.setSeckillPrice(new java.math.BigDecimal(
                    (String) stringRedisTemplate.opsForHash().get(activityKey, "seckillPrice")));
            return activity;
        }

        // Redis 未命中 → 查 DB
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.SECKILL_NOT_STARTED);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_ALREADY_ENDED);
        }
        if (activity.getStatus() != 1) {
            throw new BusinessException(ResultCode.SECKILL_ALREADY_ENDED);
        }
        return activity;
    }

    /** 从 HTTP 请求中获取客户端 IP */
    private String getCurrentIp() {
        try {
            String xff = httpServletRequest.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
            String xRealIp = httpServletRequest.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            return httpServletRequest.getRemoteAddr();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    /** 从 HTTP 请求中获取 User-Agent */
    private String getCurrentUserAgent() {
        try {
            return httpServletRequest.getHeader("User-Agent");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    /**
     * 同步创建秒杀订单（MQ 不可用时兜底，或直接同步创建）。
     * <p>
     * 注意：此方法被 {@link #executeSeckill} 内部调用，事务由调用方管理，
     * 因此不使用 @Transactional 注解避免自调用失效问题。
     * 方法签名使用 package-private 降低误用风险。
     * </p>
     */
    void createSeckillOrderSync(SeckillOrderMessage message) {
        Long activityId = message.getActivityId();
        Long userId = message.getUserId();
        Long skuId = message.getSkuId();

        try {
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String seqKey = "order:seq:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
            if (seq != null && seq == 1L) {
                stringRedisTemplate.expire(seqKey, 1, java.util.concurrent.TimeUnit.DAYS);
            }
            String orderSn = "SK" + datePart + String.format("%06d", seq != null ? seq % 1000000 : 1);

            ProductSku sku = skuMapper.selectById(skuId);
            if (sku == null) {
                log.error("SKU not found for direct order: skuId={}", skuId);
                return;
            }

            BigDecimal totalAmount = message.getSeckillPrice().multiply(
                    BigDecimal.valueOf(message.getQuantity()));

            OrderInfo order = new OrderInfo();
            order.setOrderSn(orderSn);
            order.setUserId(userId);
            order.setTotalAmount(totalAmount);
            order.setPayAmount(totalAmount);
            order.setFreightAmount(BigDecimal.ZERO);
            order.setStatus(0);
            order.setOrderType(1);
            orderInfoMapper.insert(order);

            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setActivityId(activityId);
            seckillOrder.setUserId(userId);
            seckillOrder.setOrderId(order.getId());
            seckillOrder.setOrderSn(orderSn);
            seckillOrder.setSkuId(skuId);
            seckillOrder.setSeckillPrice(message.getSeckillPrice());
            seckillOrder.setQuantity(message.getQuantity());
            seckillOrder.setStatus(0);
            seckillOrderMapper.insert(seckillOrder);

            // 秒杀专用扣减：只减 stock 不动 lock_stock；失败抛异常回滚（与 MQ 消费者一致）
            int deductResult = stockMapper.deductSeckillStock(skuId, message.getQuantity());
            if (deductResult == 0) {
                log.error("DB stock insufficient for direct seckill order, rolling back: orderSn={}, skuId={}", orderSn, skuId);
                throw new IllegalStateException("DB stock insufficient for seckill order: " + orderSn);
            }

            // 与 MQ 消费者路径保持一致：同步更新活动剩余库存
            SeckillActivity activity = activityMapper.selectById(activityId);
            if (activity != null) {
                activity.setAvailableStock(activity.getAvailableStock() - message.getQuantity());
                activityMapper.updateById(activity);
            }

            log.info("Direct seckill order created: orderSn={}", orderSn);

            // 延时关单消息（MQ 不可用场景下发送会失败，仅记录日志，与普通下单一致）
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                        RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                        orderSn,
                        msg -> {
                            msg.getMessageProperties().setExpiration(String.valueOf(30 * 60 * 1000));
                            return msg;
                        }
                );
            } catch (Exception e) {
                log.warn("Failed to send direct seckill order delay message: orderSn={}, error={}", orderSn, e.getMessage());
            }

            // 写入已完成标记，防止用户重复秒杀同一活动
            seckillLockUtil.markCompleted(activityId, userId);

            // 推送成功事件
            eventPublisher.publishSeckillResult(activityId, userId, "success",
                    "恭喜！秒杀成功", orderSn);
            SysUser user = sysUserMapper.selectById(userId);
            String username = user != null ? user.getUsername() : "用户" + userId;
            eventPublisher.publishWinnerAnnouncement(activityId, username);
        } catch (Exception e) {
            log.error("Failed to create direct seckill order: activityId={}, userId={}",
                    activityId, userId, e);
            throw e;
        }
    }
}
