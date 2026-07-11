package com.smartseckill.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀分布式锁工具（Redis + Lua 原子实现）。
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li><b>防重复点击</b>：同一用户同一活动只有第一个请求能拿到锁，后续请求全部拒绝</li>
 *   <li><b>防锁后重复购买</b>：订单创建成功后写 completed 标记，同一用户无法再次秒杀同一活动</li>
 *   <li><b>防无锁秒杀</b>：所有秒杀校验（Token、已完成、库存、用户资格）与锁获取在同一个 Lua 脚本中原子完成</li>
 *   <li><b>成功才释放</b>：锁仅在订单成功创建后释放，确保用户不会在排队期间重复提交</li>
 * </ul>
 *
 * <h3>Key 设计</h3>
 * <pre>
 *   seckill:lock:{activityId}:{userId}      →  value=userId, TTL=60s（兜底过期）
 *   seckill:stock:{activityId}              →  value=剩余库存数
 *   seckill:token:{userId}:{activityId}     →  value=token
 *   seckill:completed:{activityId}:{userId} →  value=1, TTL=2h（防重复购买）
 * </pre>
 *
 * <h3>生命周期</h3>
 * <pre>
 *   用户请求秒杀
 *     │
 *     ▼
 *   acquireLock(activityId, userId, token)  ← Lua 原子：验 Token → 查已完成 → 查锁 → 查库存 → 扣库存 → 加锁
 *     │
 *     ├─ 返回 ACQUIRED  → 发送 MQ 消息（锁仍持有）
 *     │                      │
 *     │                      ▼
 *     │                   MQ 消费者/同步兜底 创建订单成功
 *     │                      │
 *     │                      ├── markCompleted(activityId, userId)  ← 写已完成标记
 *     │                      │
 *     │                      ▼
 *     │                   releaseLock(activityId, userId)  ← 释放锁
 *     │
 *     └─ 返回其他状态 → 拒绝请求（不持有锁，无需释放）
 * </pre>
 *
 * @see SeckillTokenUtil 防重 Token（与本锁配合使用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillLockUtil {

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== Key 前缀 ====================

    /** 秒杀用户锁 Key 前缀，完整 Key = seckill:lock:{activityId}:{userId} */
    public static final String SECKILL_LOCK_PREFIX = "seckill:lock:";

    /** 秒杀已完成标记 Key 前缀，完整 Key = seckill:completed:{activityId}:{userId} */
    public static final String SECKILL_COMPLETED_PREFIX = "seckill:completed:";

    // ==================== 锁相关常量 ====================

    /**
     * 锁的兜底过期时间（秒）。
     * 正常情况下锁由消费者主动释放；此 TTL 防止因进程崩溃/网络断开导致死锁。
     * 应大于订单创建的最大耗时（MQ 消费 + DB 写入通常 &lt; 5s，取 60s 留有充足余量）。
     */
    public static final long LOCK_TTL_SECONDS = 60;

    /**
     * 已完成标记的 TTL（秒）。
     * 订单创建成功后写入，用于阻止已成功的用户再次秒杀同一活动。
     * 设为活动最长持续时间（2小时），确保活动期间持久有效。
     */
    public static final long COMPLETED_TTL_SECONDS = 7200;

    // ==================== Lua 脚本返回值常量 ====================

    /** 锁获取成功：Token 有效 + 无现有锁 + 库存充足 + 已扣库存 + 已加锁 */
    public static final long ACQUIRE_SUCCESS = 1L;
    /** Token 无效或已被使用 */
    public static final long ACQUIRE_FAIL_TOKEN_INVALID = 0L;
    /** 用户已持有锁（重复提交） */
    public static final long ACQUIRE_FAIL_ALREADY_LOCKED = -1L;
    /** 库存不足（已抢光） */
    public static final long ACQUIRE_FAIL_SOLD_OUT = -2L;
    /** 活动 Key 不存在（活动未预热或已结束） */
    public static final long ACQUIRE_FAIL_NO_STOCK_KEY = -3L;
    /** 用户已秒杀成功（已完成标记存在，防止重复购买） */
    public static final long ACQUIRE_FAIL_ALREADY_COMPLETED = -4L;

    // ==================== Lua 脚本 ====================

    /**
     * 原子获取秒杀锁的 Lua 脚本。
     *
     * <pre>
     * KEYS[1] = seckill:token:{userId}:{activityId}   — 防重 Token Key
     * KEYS[2] = seckill:lock:{activityId}:{userId}    — 用户锁 Key
     * KEYS[3] = seckill:stock:{activityId}             — 库存 Key
     * KEYS[4] = seckill:completed:{activityId}:{userId} — 已完成标记 Key
     *
     * ARGV[1] = 期望的 Token 值
     * ARGV[2] = 用户 ID（作为锁的 value）
     * ARGV[3] = 锁 TTL（秒）
     * ARGV[4] = 已完成标记 TTL（秒）
     * </pre>
     *
     * <strong>执行流程（全部在 Redis 单线程中原子完成）：</strong>
     * <ol>
     *   <li>GET + DEL Token：校验防重 Token 并立即消费</li>
     *   <li>EXISTS 已完成标记：检查用户是否已秒杀成功过（防止锁释放后重复购买）</li>
     *   <li>EXISTS 锁：检查用户是否已持有锁（防止重复提交）</li>
     *   <li>GET 库存 + DECR：检查并原子扣减库存</li>
     *   <li>SETEX 锁：加锁，值=userId，TTL=兜底过期时间</li>
     * </ol>
     *
     * @return 1=成功, 0=Token无效, -1=已锁, -2=库存不足, -3=库存Key不存在, -4=已秒杀成功
     */
    public static final String SECKILL_ACQUIRE_LOCK_LUA =
            // ========= 第1步：校验并消费防重Token =========
            "local tokenKey = KEYS[1] " +
            "local expectedToken = ARGV[1] " +
            "local actualToken = redis.call('GET', tokenKey) " +
            "if actualToken ~= expectedToken then " +
            "    return 0 " +  // Token 无效或已被使用 → ACQUIRE_FAIL_TOKEN_INVALID
            "end " +
            "redis.call('DEL', tokenKey) " +  // Token 消费（一次性使用）

            // ========= 第1.5步：检查已完成标记（防止锁释放后重复购买） =========
            "local completedKey = KEYS[4] " +
            "if redis.call('EXISTS', completedKey) == 1 then " +
            "    return -4 " +  // 已秒杀成功 → ACQUIRE_FAIL_ALREADY_COMPLETED
            "end " +

            // ========= 第2步：检查是否已持有锁（防重复提交） =========
            "local lockKey = KEYS[2] " +
            "local existingLock = redis.call('EXISTS', lockKey) " +
            "if existingLock == 1 then " +
            "    return -1 " +  // 已有锁 → ACQUIRE_FAIL_ALREADY_LOCKED
            "end " +

            // ========= 第3步：检查库存并原子扣减 =========
            "local stockKey = KEYS[3] " +
            "local stock = redis.call('GET', stockKey) " +
            "if stock == false then " +
            "    return -3 " +  // 库存 Key 不存在（活动未预热或已结束）→ ACQUIRE_FAIL_NO_STOCK_KEY
            "end " +
            "local stockNum = tonumber(stock) " +
            "if stockNum == nil or stockNum <= 0 then " +
            "    return -2 " +  // 库存不足 → ACQUIRE_FAIL_SOLD_OUT
            "end " +
            "redis.call('DECR', stockKey) " +  // 原子扣库存

            // ========= 第4步：加锁（兜底 TTL 防止死锁） =========
            "local userId = ARGV[2] " +
            "local lockTTL = tonumber(ARGV[3]) " +
            "redis.call('SETEX', lockKey, lockTTL, userId) " +

            "return 1";  // ACQUIRE_SUCCESS

    /**
     * 释放秒杀锁的 Lua 脚本（安全释放：校验锁的值是否匹配）。
     *
     * <pre>
     * KEYS[1] = seckill:lock:{activityId}:{userId}  — 用户锁 Key
     * ARGV[1] = 用户 ID（校验是否为锁的持有者）
     * </pre>
     *
     * 只有锁存在且值匹配时才删除，防止误删其他用户的锁。
     *
     * @return 1=释放成功, 0=锁不存在或不属于该用户
     */
    public static final String SECKILL_RELEASE_LOCK_LUA =
            "local lockKey = KEYS[1] " +
            "local expectedUserId = ARGV[1] " +
            "local lockValue = redis.call('GET', lockKey) " +
            "if lockValue == expectedUserId then " +
            "    redis.call('DEL', lockKey) " +
            "    return 1 " +
            "end " +
            "return 0";

    // ==================== 公开 API ====================

    /**
     * 原子获取秒杀锁（Token 校验 + 库存扣减 + 加锁，一条 Lua 完成）。
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     * @param token      客户端提交的防重 Token
     * @return 获取结果码（见本类 ACQUIRE_* 常量）
     */
    public long acquireLock(Long activityId, Long userId, String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Seckill lock acquire: empty token, userId={}, activityId={}", userId, activityId);
            return ACQUIRE_FAIL_TOKEN_INVALID;
        }

        String tokenKey = SeckillTokenUtil.SECKILL_TOKEN_PREFIX + userId + ":" + activityId;
        String lockKey = SECKILL_LOCK_PREFIX + activityId + ":" + userId;
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String completedKey = SECKILL_COMPLETED_PREFIX + activityId + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SECKILL_ACQUIRE_LOCK_LUA);
        script.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(
                script,
                Arrays.asList(tokenKey, lockKey, stockKey, completedKey),
                token,
                String.valueOf(userId),
                String.valueOf(LOCK_TTL_SECONDS),
                String.valueOf(COMPLETED_TTL_SECONDS)
        );

        long resultCode = (result != null) ? result : ACQUIRE_FAIL_TOKEN_INVALID;

        if (resultCode == ACQUIRE_SUCCESS) {
            log.info("Seckill lock acquired: activityId={}, userId={}", activityId, userId);
        } else {
            log.warn("Seckill lock acquire failed: activityId={}, userId={}, result={}",
                    activityId, userId, resultCode);
        }

        return resultCode;
    }

    /**
     * 释放秒杀锁（订单创建成功后调用）。
     * <p>
     * 此方法在以下场景调用：
     * <ul>
     *   <li>MQ 消费者创建订单成功后</li>
     *   <li>MQ 不可用时的同步兜底创建订单成功后</li>
     * </ul>
     * 锁释放失败（如锁已 TTL 过期）不影响业务流程，仅记录日志。
     * </p>
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     * @return true=释放成功, false=锁不存在或已过期
     */
    public boolean releaseLock(Long activityId, Long userId) {
        String lockKey = SECKILL_LOCK_PREFIX + activityId + ":" + userId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SECKILL_RELEASE_LOCK_LUA);
        script.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                String.valueOf(userId)
        );

        boolean released = (result != null && result == 1L);
        if (released) {
            log.info("Seckill lock released: activityId={}, userId={}", activityId, userId);
        } else {
            log.debug("Seckill lock release skipped (already released or expired): activityId={}, userId={}",
                    activityId, userId);
        }
        return released;
    }

    /**
     * 检查用户是否持有秒杀锁。
     * <p>
     * 用于查询接口判定用户状态：有锁 = 排队中（订单尚未创建），无锁 + 有订单 = 秒杀成功。
     * </p>
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     * @return true=持有锁（排队中）, false=无锁
     */
    public boolean isLocked(Long activityId, Long userId) {
        String lockKey = SECKILL_LOCK_PREFIX + activityId + ":" + userId;
        Boolean exists = stringRedisTemplate.hasKey(lockKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 获取锁的剩余 TTL（秒）。
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     * @return 剩余秒数，-1=永不过期，-2=Key 不存在
     */
    public long getLockTTL(Long activityId, Long userId) {
        String lockKey = SECKILL_LOCK_PREFIX + activityId + ":" + userId;
        Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return (ttl != null) ? ttl : -2L;
    }

    /**
     * 标记用户秒杀成功（写入已完成标记，阻止后续重复秒杀）。
     * <p>
     * 在订单创建成功后调用，标记 TTL 与锁 TTL 相同（60 秒）。
     * 该标记在 Lua 脚本的原子检查中被校验，确保同一用户不能多次秒杀同一活动。
     * </p>
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     */
    public void markCompleted(Long activityId, Long userId) {
        String completedKey = SECKILL_COMPLETED_PREFIX + activityId + ":" + userId;
        stringRedisTemplate.opsForValue().set(completedKey, "1", COMPLETED_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Seckill completed marker set: activityId={}, userId={}", activityId, userId);
    }

    /**
     * 检查用户是否已秒杀成功（已完成标记是否存在）。
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID
     * @return true=已秒杀成功, false=未参与或未成功
     */
    public boolean isCompleted(Long activityId, Long userId) {
        String completedKey = SECKILL_COMPLETED_PREFIX + activityId + ":" + userId;
        Boolean exists = stringRedisTemplate.hasKey(completedKey);
        return Boolean.TRUE.equals(exists);
    }
}
