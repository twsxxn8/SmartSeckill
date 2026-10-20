package com.smartseckill.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类。
 * <p>
 * 封装常用的 Redis 字符串操作和 Lua 脚本执行，
 * 并统一定义项目中各业务模块的 Key 前缀常量。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 验证码相关 Key ====================
    /** 验证码 Key 前缀，完整 Key = verify:code:{phone或email} */
    public static final String VERIFY_CODE_PREFIX = "verify:code:";
    /** 验证码过期时间（分钟） */
    public static final long VERIFY_CODE_TTL_MINUTES = 5;

    // ==================== 秒杀相关 Key ====================
    /** 秒杀库存 Key 前缀，完整 Key = seckill:stock:{activityId} */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";
    /** 秒杀用户参与记录 Key 前缀，用于防重复购买 */
    public static final String SECKILL_USER_PREFIX = "seckill:user:";
    /** 秒杀活动缓存 Key 前缀 */
    public static final String SECKILL_ACTIVITY_PREFIX = "seckill:activity:";

    // ==================== 购物车 Key ====================
    /** 购物车 Key 前缀，完整 Key = cart:{userId}，Hash 结构存储 SKU */
    public static final String CART_PREFIX = "cart:";

    // ==================== 限流 Key ====================
    /** 接口限流 Key 前缀 */
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";

    // ==================== Token 黑名单 Key ====================
    /** Token 黑名单 Key 前缀，完整 Key = token:blacklist:{token}，值 = "1"，TTL = token 剩余有效时间 */
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    // ==================== 秒杀在线计数 Key ====================
    /** 秒杀活动在线竞争人数 Key 前缀，ZSet 存储，score=最后活跃时间戳 */
    public static final String SECKILL_ONLINE_PREFIX = "seckill:online:";
    /** 在线用户过期时间（秒），超过此时间未活跃则从 ZSet 中移除 */
    public static final long SECKILL_ONLINE_TTL_SECONDS = 15;
    /** 秒杀中奖公告 Key 前缀，List 存储最近 N 条中奖记录 */
    public static final String SECKILL_WINNER_PREFIX = "seckill:winner:";
    /** 中奖公告最大保留条数 */
    public static final long SECKILL_WINNER_MAX_SIZE = 20;

    // ==================== 风控 Key ====================
    /** 用户行为特征 Key 前缀，供 AI 风控模型使用 */
    public static final String RISK_FEATURE_PREFIX = "risk:feature:";
    /** 人机验证通过标记 Key 前缀，完整 Key = risk:verified:{userId}:{activityId}，值 = "1" */
    public static final String RISK_VERIFIED_PREFIX = "risk:verified:";
    /** 人机验证通过标记过期时间（分钟） */
    public static final long RISK_VERIFIED_TTL_MINUTES = 10;
    /** 人机验证算术题答案 Key 前缀，完整 Key = risk:verify:challenge:{token} */
    public static final String RISK_VERIFY_CHALLENGE_PREFIX = "risk:verify:challenge:";
    /** 风控黑名单 Key 前缀，完整 Key = risk:blacklist:{userId}，值 = "1" */
    public static final String RISK_BLACKLIST_PREFIX = "risk:blacklist:";
    /** 风控 BLOCK 计数 Key 前缀，完整 Key = risk:block:count:{userId} */
    public static final String RISK_BLOCK_COUNT_PREFIX = "risk:block:count:";
    /** 黑名单与 BLOCK 计数过期时间（小时） */
    public static final long RISK_BLACKLIST_TTL_HOURS = 24;

    // ==================== 基础操作 ====================

    /** 设置 Key-Value 并指定过期时间 */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取 Key 对应的 Value */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 删除指定 Key */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /** 判断 Key 是否存在 */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /** 原子自增 1（常用于计数器、限流） */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /** 原子自增指定步长 */
    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /** 原子自减 1 */
    public Long decrement(String key) {
        return stringRedisTemplate.opsForValue().decrement(key);
    }

    // ==================== Lua 脚本执行 ====================

    /**
     * 在 Redis 服务端执行 Lua 脚本（保证原子性）。
     *
     * @param luaScript Lua 脚本内容
     * @param key       KEYS[1]
     * @param args      ARGV 参数
     * @return 脚本返回值
     */
    public Long executeLuaScript(String luaScript, String key, String... args) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);
        return stringRedisTemplate.execute(script,
                Collections.singletonList(key),
                args);
    }

    // ==================== 秒杀库存 Lua 脚本 ====================

    /**
     * 原子预减库存 Lua 脚本。
     * <p>
     * KEYS[1] = 库存 key<br>
     * 返回: 1=预减成功, 0=库存不足或 key 不存在
     * </p>
     */
    public static final String SECKILL_DEDUCT_STOCK_LUA =
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if (stock == nil) then " +
            "    return 0 " +
            "end " +
            "if (stock <= 0) then " +
            "    return 0 " +
            "end " +
            "redis.call('decr', KEYS[1]) " +
            "return 1";

    /**
     * 秒杀预减库存（Redis 原子操作，防止超卖）。
     *
     * @param stockKey 库存 Redis Key
     * @return true=预减成功, false=库存不足
     */
    public boolean preDeductStock(String stockKey) {
        Long result = executeLuaScript(SECKILL_DEDUCT_STOCK_LUA, stockKey);
        return result != null && result == 1L;
    }
}
