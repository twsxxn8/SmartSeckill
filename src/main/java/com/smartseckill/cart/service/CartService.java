package com.smartseckill.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseckill.cart.dto.CartItemDTO;
import com.smartseckill.common.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 购物车业务服务。
 * <p>
 * RedissonClient 使用 {@link Lazy} 注入：不到真正调用 addToCart 时不初始化，
 * 避免 Redis 不可用时应用无法启动。
 * </p>
 */
@Slf4j
@Service
public class CartService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    public CartService(StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper,
                       @Lazy RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
    }

    private static final String CART_LOCK_PREFIX = "cart:lock:";

    private String getCartKey(Long userId) {
        return RedisUtil.CART_PREFIX + userId;
    }

    private String getLockKey(Long userId, Long skuId) {
        return CART_LOCK_PREFIX + userId + ":" + skuId;
    }

    /**
     * 添加商品到购物车（分布式锁保证并发安全）。
     * 若 SKU 已存在则累加数量，否则新增 Hash 字段。
     */
    public void addToCart(Long userId, CartItemDTO item) {
        String cartKey = getCartKey(userId);
        String skuIdStr = String.valueOf(item.getSkuId());
        RLock lock = redissonClient.getLock(getLockKey(userId, item.getSkuId()));

        try {
            // 细粒度锁：仅锁定当前用户的当前 SKU，不影响其他 SKU 或其他用户
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.warn("Failed to acquire cart lock: userId={}, skuId={}", userId, item.getSkuId());
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            String existing = (String) stringRedisTemplate.opsForHash().get(cartKey, skuIdStr);
            if (existing != null) {
                CartItemDTO existingItem = objectMapper.readValue(existing, CartItemDTO.class);
                existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
                stringRedisTemplate.opsForHash().put(cartKey, skuIdStr,
                        objectMapper.writeValueAsString(existingItem));
            } else {
                stringRedisTemplate.opsForHash().put(cartKey, skuIdStr,
                        objectMapper.writeValueAsString(item));
            }

            stringRedisTemplate.expire(cartKey, 30, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("购物车操作被中断", e);
        } catch (Exception e) {
            log.error("Error in addToCart", e);
            throw new RuntimeException("购物车操作失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 获取购物车全部商品 */
    public List<CartItemDTO> getCartItems(Long userId) {
        String cartKey = getCartKey(userId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(cartKey);

        List<CartItemDTO> items = new ArrayList<>();
        for (Object value : entries.values()) {
            try {
                items.add(objectMapper.readValue(value.toString(), CartItemDTO.class));
            } catch (Exception e) {
                log.error("Error parsing cart item", e);
            }
        }
        return items;
    }

    /** 更新某 SKU 的购买数量 */
    public void updateQuantity(Long userId, Long skuId, Integer quantity) {
        String cartKey = getCartKey(userId);
        String skuIdStr = String.valueOf(skuId);

        String existing = (String) stringRedisTemplate.opsForHash().get(cartKey, skuIdStr);
        if (existing != null) {
            try {
                CartItemDTO item = objectMapper.readValue(existing, CartItemDTO.class);
                item.setQuantity(quantity);
                stringRedisTemplate.opsForHash().put(cartKey, skuIdStr,
                        objectMapper.writeValueAsString(item));
            } catch (Exception e) {
                log.error("Error updating cart quantity", e);
            }
        }
    }

    /** 从购物车移除单个 SKU */
    public void removeFromCart(Long userId, Long skuId) {
        String cartKey = getCartKey(userId);
        stringRedisTemplate.opsForHash().delete(cartKey, String.valueOf(skuId));
    }

    /** 清空整个购物车 */
    public void clearCart(Long userId) {
        stringRedisTemplate.delete(getCartKey(userId));
    }

    /** 获取购物车中 SKU 种类数 */
    public Long getCartCount(Long userId) {
        return stringRedisTemplate.opsForHash().size(getCartKey(userId));
    }

    /** 下单成功后批量移除已购 SKU */
    public void removeItems(Long userId, List<Long> skuIds) {
        String cartKey = getCartKey(userId);
        String[] keys = skuIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);
        stringRedisTemplate.opsForHash().delete(cartKey, (Object[]) keys);
    }
}
