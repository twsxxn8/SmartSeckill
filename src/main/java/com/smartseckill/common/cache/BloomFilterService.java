package com.smartseckill.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 布隆过滤器服务 — 防缓存穿透的第一道防线。
 *
 * <h3>为什么用布隆过滤器而不是空值缓存</h3>
 * <p>
 * 空值缓存只能防护"同一个 ID 反复查询"的穿透，但无法防护"遍历 ID"型攻击——
 * 恶意用户用 productId=1, 2, 3, ... 轮询，每个新 ID 都会穿透到 DB。
 * 布隆过滤器用固定大小的 bitmap 即可判断"这个 ID 绝对不存在"，
 * 把非法请求挡在 Redis 之前。
 * </p>
 *
 * <h3>为什么用 Redisson RBloomFilter 而不是 Guava 本地</h3>
 * <ul>
 *   <li><b>分布式一致</b>：所有应用实例共享同一个过滤器（存储在 Redis）</li>
 *   <li><b>持久化</b>：应用重启无需重建（Redis 不重启就不会丢）</li>
 *   <li><b>扩容方便</b>：新实例启动即可用，无需同步数据</li>
 * </ul>
 *
 * <h3>参数设计</h3>
 * <pre>
 *   误判率 = 1% (0.01)
 *   — 99% 的非法请求被过滤，内存占用极小
 *   — 1% 误判的请求仍会走 Cache Aside → 空值缓存兜底
 *
 *   容量预估：
 *   | 过滤器            | 预计元素  | 内存占用 |
 *   |-------------------|----------|---------|
 *   | product:spu:id    | 10,000   | ~12 KB  |
 *   | seckill:activity:id| 1,000   | ~1.5 KB |
 *   | coupon:id         | 500      | ~1 KB   |
 * </pre>
 *
 * <h3>与 CacheService 的协作</h3>
 * <pre>
 *   请求 → BloomFilter.mightContain(id)
 *          │
 *          ├─ false → 直接返回 null（绝对不存在，0次 DB 查询）
 *          │
 *          └─ true  → Cache Aside 正常流程
 *                      │
 *                      └─ DB 也查不到 → 写空值缓存（兜底 1% 误判）
 * </pre>
 */
@Slf4j
@Component
public class BloomFilterService {

    private final RedissonClient redissonClient;

    /** 商品 SPU ID 布隆过滤器 */
    private RBloomFilter<Long> productSpuFilter;

    /** 秒杀活动 ID 布隆过滤器 */
    private RBloomFilter<Long> seckillActivityFilter;

    /** 优惠券 ID 布隆过滤器 */
    private RBloomFilter<Long> couponFilter;

    // ==================== Redis Key ====================
    public static final String BF_PRODUCT_SPU = "bloom:product:spu";
    public static final String BF_SECKILL_ACTIVITY = "bloom:seckill:activity";
    public static final String BF_COUPON = "bloom:coupon";

    // ==================== 参数 ====================
    /** 误判率 1% — 平衡内存和防护效果 */
    private static final double FALSE_PROBABILITY = 0.01;

    public BloomFilterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 初始化三个布隆过滤器（懒加载，首次调用时才真正创建）。
     * 不在 @PostConstruct 中创建，避免 Redisson 未就绪时 NPE。
     */
    @PostConstruct
    public void init() {
        // getBloomFilter 内部其实是懒初始化
        // 这里显式调用确保对象非 null
        this.productSpuFilter = redissonClient.getBloomFilter(BF_PRODUCT_SPU);
        this.seckillActivityFilter = redissonClient.getBloomFilter(BF_SECKILL_ACTIVITY);
        this.couponFilter = redissonClient.getBloomFilter(BF_COUPON);
        log.info("BloomFilter references initialized (lazy — will be populated on first use)");
    }

    // ==================== 判断存在性 ====================

    /**
     * 判断商品 SPU ID 是否可能存在。
     *
     * @param spuId 商品 SPU ID
     * @return false=绝对不存在（直接拒绝），true=可能存在（继续走 Cache Aside）
     */
    public boolean mightContainSpu(Long spuId) {
        try {
            return productSpuFilter.contains(spuId);
        } catch (Exception e) {
            // Redisson 连不上时降级：不拦截，让 Cache Aside + 空值缓存兜底
            log.warn("BloomFilter SPU check failed (fallback to pass-through): {}", e.getMessage());
            return true;
        }
    }

    /**
     * 判断秒杀活动 ID 是否可能存在。
     */
    public boolean mightContainActivity(Long activityId) {
        try {
            return seckillActivityFilter.contains(activityId);
        } catch (Exception e) {
            log.warn("BloomFilter activity check failed (fallback to pass-through): {}", e.getMessage());
            return true;
        }
    }

    /**
     * 判断优惠券 ID 是否可能存在。
     */
    public boolean mightContainCoupon(Long couponId) {
        try {
            return couponFilter.contains(couponId);
        } catch (Exception e) {
            log.warn("BloomFilter coupon check failed (fallback to pass-through): {}", e.getMessage());
            return true;
        }
    }

    // ==================== 添加元素（实体创建时调用） ====================

    /** 新商品创建时加入过滤器 */
    public void addSpu(Long spuId) {
        try {
            productSpuFilter.add(spuId);
            log.debug("BloomFilter: added SPU {}", spuId);
        } catch (Exception e) {
            log.warn("BloomFilter: failed to add SPU {}", spuId);
        }
    }

    /** 新秒杀活动创建时加入过滤器 */
    public void addActivity(Long activityId) {
        try {
            seckillActivityFilter.add(activityId);
            log.debug("BloomFilter: added activity {}", activityId);
        } catch (Exception e) {
            log.warn("BloomFilter: failed to add activity {}", activityId);
        }
    }

    /** 新优惠券创建时加入过滤器 */
    public void addCoupon(Long couponId) {
        try {
            couponFilter.add(couponId);
            log.debug("BloomFilter: added coupon {}", couponId);
        } catch (Exception e) {
            log.warn("BloomFilter: failed to add coupon {}", couponId);
        }
    }

    // ==================== 批量初始化（启动预热时调用） ====================

    /**
     * 用所有已有 SPU ID 填充过滤器。首次调用 tryInit + 批量 add。
     *
     * @param spuIds 所有有效 SPU ID 列表
     * @param expectedInsertions 预期最大元素数（用于计算 bitmap 大小）
     */
    public void initSpuFilter(java.util.List<Long> spuIds, long expectedInsertions) {
        try {
            boolean created = productSpuFilter.tryInit(expectedInsertions, FALSE_PROBABILITY);
            if (created) {
                log.info("BloomFilter [product:spu] created: expected={}, fpRate={}", expectedInsertions, FALSE_PROBABILITY);
            }
            for (Long id : spuIds) {
                productSpuFilter.add(id);
            }
            log.info("BloomFilter [product:spu] loaded {} IDs", spuIds.size());
        } catch (Exception e) {
            log.error("BloomFilter [product:spu] init failed: {}", e.getMessage());
        }
    }

    /**
     * 用所有已有活动 ID 填充过滤器。
     */
    public void initActivityFilter(java.util.List<Long> activityIds, long expectedInsertions) {
        try {
            boolean created = seckillActivityFilter.tryInit(expectedInsertions, FALSE_PROBABILITY);
            if (created) {
                log.info("BloomFilter [seckill:activity] created: expected={}, fpRate={}", expectedInsertions, FALSE_PROBABILITY);
            }
            for (Long id : activityIds) {
                seckillActivityFilter.add(id);
            }
            log.info("BloomFilter [seckill:activity] loaded {} IDs", activityIds.size());
        } catch (Exception e) {
            log.error("BloomFilter [seckill:activity] init failed: {}", e.getMessage());
        }
    }

    /**
     * 用所有已有优惠券 ID 填充过滤器。
     */
    public void initCouponFilter(java.util.List<Long> couponIds, long expectedInsertions) {
        try {
            boolean created = couponFilter.tryInit(expectedInsertions, FALSE_PROBABILITY);
            if (created) {
                log.info("BloomFilter [coupon] created: expected={}, fpRate={}", expectedInsertions, FALSE_PROBABILITY);
            }
            for (Long id : couponIds) {
                couponFilter.add(id);
            }
            log.info("BloomFilter [coupon] loaded {} IDs", couponIds.size());
        } catch (Exception e) {
            log.error("BloomFilter [coupon] init failed: {}", e.getMessage());
        }
    }
}
