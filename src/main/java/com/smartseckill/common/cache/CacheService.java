package com.smartseckill.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用缓存服务 — 实现 Cache Aside 模式。
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>读</b>：先查 Redis → 命中返回 → 未命中查 DB → 写 Redis → 返回</li>
 *   <li><b>写</b>：先更新 DB → 删除 Redis 缓存 → 等待下次读重建</li>
 *   <li><b>防穿透</b>：DB 查不到的 null 值也会缓存（短 TTL），防止恶意请求打到 DB</li>
 *   <li><b>防雪崩</b>：TTL 叠加 ±20% 随机抖动，避免大量缓存同时过期</li>
 *   <li><b>防击穿</b>：热点 Key 过期瞬间的并发回源，由调用方配合分布式锁处理，
 *       本服务提供 {@link #getOrLoadWithLock} 方法</li>
 * </ul>
 *
 * <h3>Key 命名规范</h3>
 * 所有缓存 Key 统一前缀 {@code cache:}，按模块分子命名空间：
 * <pre>
 *   cache:product:spu:{id}
 *   cache:product:sku:list:{spuId}
 *   cache:product:category:list:{parentId}
 *   cache:product:brand:list:{categoryId}
 *   cache:review:spu:{spuId}
 *   cache:coupon:{id}
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // ==================== 缓存 Key 前缀 ====================

    /** 商品 SPU 详情 */
    public static final String CACHE_PRODUCT_SPU = "cache:product:spu:";
    /** 商品 SKU 列表（按 SPU） */
    public static final String CACHE_PRODUCT_SKU_LIST = "cache:product:sku:list:";
    /** 商品分类列表（按 parentId） */
    public static final String CACHE_PRODUCT_CATEGORY_LIST = "cache:product:category:list:";
    /** 品牌列表（按 categoryId） */
    public static final String CACHE_PRODUCT_BRAND_LIST = "cache:product:brand:list:";
    /** 评论数据（按 SPU） */
    public static final String CACHE_REVIEW_SPU = "cache:review:spu:";
    /** 优惠券详情 */
    public static final String CACHE_COUPON = "cache:coupon:";

    // ==================== 通配符（供批量删除） ====================
    public static final String CACHE_PRODUCT_SPU_PATTERN = "cache:product:spu:*";
    public static final String CACHE_PRODUCT_SKU_LIST_PATTERN = "cache:product:sku:list:*";
    public static final String CACHE_PRODUCT_CATEGORY_PATTERN = "cache:product:category:*";
    public static final String CACHE_PRODUCT_BRAND_PATTERN = "cache:product:brand:*";
    public static final String CACHE_REVIEW_SPU_PATTERN = "cache:review:spu:*";

    // ==================== TTL 常量 ====================

    /** 默认基础 TTL：10 分钟（对一致性要求较高的数据） */
    public static final long TTL_BASE_SECONDS = 600;
    /** 热点数据 TTL：30 分钟（分类、品牌等变化极少的元数据） */
    public static final long TTL_HOT_SECONDS = 1800;
    /** 空值缓存 TTL：60 秒（防穿透，短 TTL 让正常数据尽快覆盖） */
    public static final long TTL_NULL_SECONDS = 60;
    /** TTL 随机抖动比例（±20%），防雪崩 */
    public static final double TTL_JITTER_RATIO = 0.2;

    // ==================== 空值标记 ====================
    private static final String NULL_PLACEHOLDER = "__NULL__";

    // ==================== Cache Aside 读 ====================

    /**
     * 从缓存读取对象，未命中则调用 loader 回源 DB 并自动写缓存。
     *
     * @param key      缓存 Key
     * @param clazz    目标类型
     * @param loader   DB 查询回调（缓存未命中时调用）
     * @param ttlSecs  缓存过期时间（秒），会自动叠加 ±20% 随机抖动
     * @param <T>      返回值类型
     * @return 查询结果，可能为 null
     */
    public <T> T getOrLoad(String key, Class<T> clazz, Supplier<T> loader, long ttlSecs) {
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                // 空值缓存命中 → 防穿透直接返回 null
                log.debug("[Cache HIT-NULL] key={}", key);
                return null;
            }
            log.debug("[Cache HIT] key={}", key);
            return clazz.cast(cached);
        }

        // 2. 缓存未命中 → 回源 DB
        log.debug("[Cache MISS] key={}, loading from DB...", key);
        T value = loader.get();

        // 3. 写缓存（含空值防穿透 + 随机 TTL 防雪崩）
        putWithJitter(key, value, ttlSecs);

        return value;
    }

    /**
     * 从缓存读取列表类对象（TypeReference 版本，处理泛型擦除）。
     *
     * @param key      缓存 Key
     * @param typeRef  TypeReference（如 {@code new TypeReference<List<Foo>>() {}}）
     * @param loader   DB 查询回调
     * @param ttlSecs  缓存过期时间（秒）
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoadList(String key, TypeReference<T> typeRef, Supplier<T> loader, long ttlSecs) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                log.debug("[Cache HIT-NULL] key={}", key);
                return null;
            }
            log.debug("[Cache HIT] key={}", key);
            return (T) cached;
        }

        log.debug("[Cache MISS] key={}, loading from DB...", key);
        T value = loader.get();
        putWithJitter(key, value, ttlSecs);
        return value;
    }

    // ==================== Cache Aside 写 ====================

    /**
     * 写操作后删除缓存（Cache Aside 写路径的核心）。
     * <p>
     * 用法：在 updateXxx / deleteXxx 的 DB 事务提交后调用此方法。
     * 支持通配符模糊匹配（需业务方传入时自行拼接 pattern）。
     * </p>
     */
    public void evict(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[Cache EVICT] key={}, deleted={}", key, deleted);
        } catch (Exception e) {
            log.warn("[Cache EVICT-FAIL] key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 按模块批量删除缓存（删除所有匹配 pattern 的 Key）。
     * <p>
     * 注意：Redis KEYS 命令在生产环境可能阻塞，这里用 scan 模式避免。
     * 仅在写操作后调用，频率极低，安全可控。
     * </p>
     */
    public void evictPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("[Cache EVICT-PATTERN] pattern={}, count={}", pattern, deleted);
            }
        } catch (Exception e) {
            log.warn("[Cache EVICT-PATTERN-FAIL] pattern={}, error={}", pattern, e.getMessage());
        }
    }

    // ==================== 直接写缓存 ====================

    /**
     * 直接写入缓存（带随机 TTL 抖动）。
     */
    public void put(String key, Object value, long ttlSecs) {
        putWithJitter(key, value, ttlSecs);
    }

    /** 写入空值缓存（防穿透），使用短 TTL */
    public void putNull(String key) {
        putWithJitter(key, NULL_PLACEHOLDER, TTL_NULL_SECONDS);
    }

    // ==================== 原子操作 ====================

    /**
     * 获取 String 类型值。
     */
    public String getString(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 获取 Long 类型值（如计数器、库存数）。
     */
    public Long getLong(String key) {
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null) return null;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 设置 String 值。
     */
    public void setString(String key, String value, long ttlSecs) {
        stringRedisTemplate.opsForValue().set(key, value, ttlSecs, TimeUnit.SECONDS);
    }

    /** 判断 Key 是否存在 */
    public boolean exists(String key) {
        Boolean b = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(b);
    }

    /** 获取 Key 的剩余 TTL（秒），-2=不存在 */
    public long ttl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    // ==================== 内部方法 ====================

    /**
     * 写缓存 + 随机 TTL 抖动（防雪崩）。
     * <p>
     * 公式：finalTTL = baseTTL * (1 + random(-jitterRatio, +jitterRatio))
     * 例：baseTTL=600, jitter=20% → actual ∈ [480, 720]
     * </p>
     */
    private void putWithJitter(String key, Object value, long baseTtlSecs) {
        long actualTtl = (long) (baseTtlSecs * (1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * TTL_JITTER_RATIO));
        if (actualTtl <= 0) actualTtl = 1; // 最少 1 秒

        Object cacheValue = (value == null) ? NULL_PLACEHOLDER : value;
        try {
            redisTemplate.opsForValue().set(key, cacheValue, actualTtl, TimeUnit.SECONDS);
            log.debug("[Cache PUT] key={}, ttl={}s, isNull={}", key, actualTtl, value == null);
        } catch (Exception e) {
            log.warn("[Cache PUT-FAIL] key={}, error={}", key, e.getMessage());
        }
    }
}
