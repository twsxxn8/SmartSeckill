package com.smartseckill.common.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.coupon.entity.Coupon;
import com.smartseckill.coupon.mapper.CouponMapper;
import com.smartseckill.product.entity.ProductBrand;
import com.smartseckill.product.entity.ProductCategory;
import com.smartseckill.product.entity.ProductSpu;
import com.smartseckill.product.mapper.ProductBrandMapper;
import com.smartseckill.product.mapper.ProductCategoryMapper;
import com.smartseckill.product.mapper.ProductSpuMapper;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用启动缓存预热器。
 *
 * <h3>预热策略</h3>
 * <p>
 * 启动时主动加载高频访问的数据到 Redis，避免"缓存击穿"——
 * 应用刚启动、缓存为空时大量请求直接打到数据库。
 * </p>
 *
 * <h3>预热内容及优先级</h3>
 * <ol>
 *   <li><b>商品分类</b> — 首页导航依赖，访问频率极高</li>
 *   <li><b>品牌列表</b> — 商品筛选依赖</li>
 *   <li><b>进行中的秒杀活动</b> — 恢复 Redis 库存数据（Redis 重启场景）</li>
 * </ol>
 *
 * <h3>不预热的内容（按需加载）</h3>
 * <ul>
 *   <li>商品详情（SPU/SKU）— 数据量大，首次访问时 Cache Aside 自动加载</li>
 *   <li>评论数据 — 数据量大，首次访问时 Cache Aside 自动加载</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <p>
 * 预热过程不会阻塞应用启动（异步执行），预热的缓存会在 TTL 到期后自动失效，
 * 由 CacheService 的 Cache Aside 模式自动重建。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpRunner implements CommandLineRunner {

    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSpuMapper spuMapper;
    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final CouponMapper couponMapper;
    private final CacheService cacheService;
    private final BloomFilterService bloomFilterService;
    private final StringRedisTemplate stringRedisTemplate;
    private final org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    /**
     * 获取秒杀订单队列就绪消息深度（与 StockReconciliationJob 共用同一策略）。
     * MQ 不可用时返回 0 并告警。
     */
    private int getSeckillQueueDepth() {
        try {
            java.util.Properties props = rabbitAdmin.getQueueProperties(
                    com.smartseckill.common.config.RabbitMQConfig.SECKILL_ORDER_QUEUE);
            if (props != null) {
                Object count = props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_MESSAGE_COUNT);
                return count != null ? Integer.parseInt(count.toString()) : 0;
            }
        } catch (Exception e) {
            log.warn("  [Seckill] Failed to query queue depth (treated as 0): {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public void run(String... args) {
        log.info("==================== Cache Warm-Up Start ====================");
        long startTime = System.currentTimeMillis();

        try {
            warmUpBloomFilters();
            warmUpCategories();
            warmUpBrands();
            recoverSeckillStock();
        } catch (Exception e) {
            log.error("Cache warm-up partially failed (app will still start normally): {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("==================== Cache Warm-Up Done ({}ms) ====================", elapsed);
    }

    /** 预热商品分类（首页导航核心数据） */
    private void warmUpCategories() {
        try {
            // 预热根分类（parentId=0 或 null）
            List<ProductCategory> categories = categoryMapper.selectList(
                    new LambdaQueryWrapper<ProductCategory>()
                            .eq(ProductCategory::getStatus, 1)
                            .eq(ProductCategory::getDeleted, 0)
                            .orderByAsc(ProductCategory::getSortOrder)
            );
            if (!categories.isEmpty()) {
                // 按 parentId 分组写入缓存
                categories.stream()
                        .map(ProductCategory::getParentId)
                        .distinct()
                        .forEach(parentId -> {
                            String cacheKey = CacheService.CACHE_PRODUCT_CATEGORY_LIST
                                    + (parentId != null ? parentId : "root");
                            List<ProductCategory> subList = categories.stream()
                                    .filter(c -> java.util.Objects.equals(c.getParentId(), parentId))
                                    .collect(Collectors.toList());
                            cacheService.put(cacheKey, subList, CacheService.TTL_HOT_SECONDS);
                        });
                log.info("  [Categories] {} categories warmed up", categories.size());
            }
        } catch (Exception e) {
            log.warn("  [Categories] warm-up skipped: {}", e.getMessage());
        }
    }

    /** 预热品牌列表 */
    private void warmUpBrands() {
        try {
            List<ProductBrand> brands = brandMapper.selectList(
                    new LambdaQueryWrapper<ProductBrand>()
                            .eq(ProductBrand::getStatus, 1)
                            .eq(ProductBrand::getDeleted, 0)
                            .orderByAsc(ProductBrand::getSortOrder)
            );
            if (!brands.isEmpty()) {
                cacheService.put(CacheService.CACHE_PRODUCT_BRAND_LIST + "all",
                        brands, CacheService.TTL_HOT_SECONDS);
                log.info("  [Brands] {} brands warmed up", brands.size());
            }
        } catch (Exception e) {
            log.warn("  [Brands] warm-up skipped: {}", e.getMessage());
        }
    }

    /**
     * 恢复秒杀库存数据（Redis 重启/清空后的关键恢复）。
     * <p>
     * 恢复逻辑：
     * <ol>
     *   <li>查询所有进行中的秒杀活动</li>
     *   <li>统计该活动有效订单数（DB 真实数据，排除已取消 status=4）</li>
     *   <li>Redis 库存 = totalStock - 有效订单数 - MQ 就绪队列深度</li>
     *   <li>同时恢复活动缓存 Hash</li>
     * </ol>
     * 为什么扣队列深度：Redis 数据丢失时锁也随之丢失，但 RabbitMQ 持久化队列中的
     * 积压消息仍会被消费落库（落库时不再扣 Redis）——不预先扣除会超卖。
     * 此逻辑与 {@code StockReconciliationJob} 的 Key 丢失分支一致。
     * </p>
     */
    private void recoverSeckillStock() {
        try {
            List<SeckillActivity> activeActivities = activityMapper.selectList(
                    new LambdaQueryWrapper<SeckillActivity>()
                            .eq(SeckillActivity::getStatus, 1)
                            .eq(SeckillActivity::getDeleted, 0)
            );

            if (activeActivities.isEmpty()) {
                log.info("  [Seckill] No active seckill activities, skip stock recovery");
                return;
            }

            int queueDepth = getSeckillQueueDepth();
            int recovered = 0;
            for (SeckillActivity activity : activeActivities) {
                Long activityId = activity.getId();
                String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;

                // 检查 Redis 是否已有库存数据
                String existingStock = stringRedisTemplate.opsForValue().get(stockKey);
                if (existingStock != null) {
                    // 库存 Key 已存在（Redis 未重启或已被其他机制恢复），跳过
                    continue;
                }

                // 统计有效订单数（排除已取消：取消后库存已回补）
                Long orderCount = seckillOrderMapper.selectCount(
                        new LambdaQueryWrapper<SeckillOrder>()
                                .eq(SeckillOrder::getActivityId, activityId)
                                .ne(SeckillOrder::getStatus, 4)
                );

                int totalStock = activity.getTotalStock() != null ? activity.getTotalStock() : 0;
                int redisStock = Math.max(0, totalStock - orderCount.intValue() - queueDepth);

                // 恢复 Redis 库存
                stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(redisStock));

                // 恢复活动缓存 Hash
                String activityKey = RedisUtil.SECKILL_ACTIVITY_PREFIX + activityId;
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

                recovered++;
                log.info("  [Seckill] Activity {} stock recovered: {} (total={}, orders={}, queueDepth={})",
                        activityId, redisStock, totalStock, orderCount, queueDepth);
            }

            if (recovered > 0) {
                log.warn("  [Seckill] {} seckill activities stock recovered — Redis may have been restarted", recovered);
            } else {
                log.info("  [Seckill] All {} active activities have valid stock in Redis, no recovery needed",
                        activeActivities.size());
            }
        } catch (Exception e) {
            log.error("  [Seckill] Stock recovery failed: {}", e.getMessage(), e);
        }
    }

    // ==================== 布隆过滤器初始化 ====================

    /**
     * 初始化所有布隆过滤器（防缓存穿透第一道防线）。
     * <p>
     * 从 DB 加载所有有效 ID，批量写入布隆过滤器。
     * 之后任何查询先过布隆 → 不存在的 ID 直接返回 null，不碰缓存也不查 DB。
     * </p>
     */
    private void warmUpBloomFilters() {
        try {
            // 1. 商品 SPU
            List<ProductSpu> allSpu = spuMapper.selectList(
                    new LambdaQueryWrapper<ProductSpu>()
                            .eq(ProductSpu::getDeleted, 0)
            );
            List<Long> spuIds = allSpu.stream()
                    .map(ProductSpu::getId)
                    .collect(Collectors.toList());
            if (!spuIds.isEmpty()) {
                // 预期容量 = 当前数量 × 1.5（预留增长空间）
                bloomFilterService.initSpuFilter(spuIds, spuIds.size() * 2L);
            } else {
                bloomFilterService.initSpuFilter(spuIds, 1000L);
            }

            // 2. 秒杀活动
            List<SeckillActivity> allActivities = activityMapper.selectList(
                    new LambdaQueryWrapper<SeckillActivity>()
                            .eq(SeckillActivity::getDeleted, 0)
            );
            List<Long> activityIds = allActivities.stream()
                    .map(SeckillActivity::getId)
                    .collect(Collectors.toList());
            if (!activityIds.isEmpty()) {
                bloomFilterService.initActivityFilter(activityIds, activityIds.size() * 2L);
            } else {
                bloomFilterService.initActivityFilter(activityIds, 500L);
            }

            // 3. 优惠券
            List<Coupon> allCoupons = couponMapper.selectList(
                    new LambdaQueryWrapper<Coupon>()
                            .eq(Coupon::getDeleted, 0)
            );
            List<Long> couponIds = allCoupons.stream()
                    .map(Coupon::getId)
                    .collect(Collectors.toList());
            if (!couponIds.isEmpty()) {
                bloomFilterService.initCouponFilter(couponIds, couponIds.size() * 2L);
            } else {
                bloomFilterService.initCouponFilter(couponIds, 300L);
            }

            log.info("  [BloomFilter] All filters initialized: SPU={}, Activity={}, Coupon={}",
                    spuIds.size(), activityIds.size(), couponIds.size());
        } catch (Exception e) {
            log.error("  [BloomFilter] Init failed (app will fallback to null-cache): {}", e.getMessage());
        }
    }
}
