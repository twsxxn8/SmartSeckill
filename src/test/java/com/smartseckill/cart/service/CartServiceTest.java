package com.smartseckill.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseckill.cart.dto.CartItemDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private CartService cartService;

    @Test
    void addToCart_firstItem_shouldNotThrowNpe() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cart:1", "100")).thenReturn(null);
        when(objectMapper.writeValueAsString(any(CartItemDTO.class)))
                .thenReturn("{\"skuId\":100,\"quantity\":1}");

        CartItemDTO item = new CartItemDTO();
        item.setSkuId(100L);
        item.setSpuId(10L);
        item.setPrice(BigDecimal.TEN);
        item.setQuantity(1);

        assertDoesNotThrow(() -> cartService.addToCart(1L, item));
        verify(hashOperations).put(eq("cart:1"), eq("100"), anyString());
    }

    @Test
    void addToCart_existingItem_shouldMergeQuantity() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("cart:1", "100"))
                .thenReturn("{\"skuId\":100,\"quantity\":2}");

        CartItemDTO existingItem = new CartItemDTO();
        existingItem.setSkuId(100L);
        existingItem.setQuantity(2);
        when(objectMapper.readValue(anyString(), eq(CartItemDTO.class))).thenReturn(existingItem);
        when(objectMapper.writeValueAsString(any(CartItemDTO.class)))
                .thenReturn("{\"skuId\":100,\"quantity\":3}");

        CartItemDTO item = new CartItemDTO();
        item.setSkuId(100L);
        item.setQuantity(1);

        cartService.addToCart(1L, item);

        verify(objectMapper).writeValueAsString(existingItem);
        verify(hashOperations).put(eq("cart:1"), eq("100"), anyString());
    }
}
