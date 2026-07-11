package com.smartseckill.order.service;

import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.entity.OrderItem;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.order.mapper.OrderItemMapper;
import com.smartseckill.order.mapper.OrderStatusLogMapper;
import com.smartseckill.product.mapper.ProductSkuMapper;
import com.smartseckill.product.mapper.ProductStockMapper;
import com.smartseckill.user.mapper.UserAddressMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceOwnershipTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderStatusLogMapper statusLogMapper;
    @Mock
    private ProductSkuMapper skuMapper;
    @Mock
    private ProductStockMapper stockMapper;
    @Mock
    private UserAddressMapper addressMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    void getUserOrderBySn_owner_shouldReturnOrder() {
        OrderInfo order = order("SN001", 1L);
        when(orderInfoMapper.selectOne(any())).thenReturn(order);

        OrderInfo result = orderService.getUserOrderBySn(1L, "SN001");

        assertEquals("SN001", result.getOrderSn());
        assertEquals(1L, result.getUserId());
    }

    @Test
    void getUserOrderBySn_otherUser_shouldThrowForbidden() {
        when(orderInfoMapper.selectOne(any())).thenReturn(order("SN001", 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getUserOrderBySn(1L, "SN001"));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void getUserOrderItems_owner_shouldReturnItems() {
        OrderInfo order = order("SN001", 1L);
        when(orderInfoMapper.selectOne(any())).thenReturn(order);

        OrderItem item = new OrderItem();
        item.setOrderSn("SN001");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        List<OrderItem> items = orderService.getUserOrderItems(1L, "SN001");

        assertEquals(1, items.size());
        verify(orderItemMapper).selectList(any());
    }

    @Test
    void getUserOrderItems_otherUser_shouldThrowForbiddenAndSkipItemQuery() {
        when(orderInfoMapper.selectOne(any())).thenReturn(order("SN001", 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getUserOrderItems(1L, "SN001"));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(orderItemMapper, never()).selectList(any());
    }

    private OrderInfo order(String orderSn, Long userId) {
        OrderInfo order = new OrderInfo();
        order.setOrderSn(orderSn);
        order.setUserId(userId);
        return order;
    }
}
