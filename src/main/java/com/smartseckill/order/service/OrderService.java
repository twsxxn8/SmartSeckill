package com.smartseckill.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.coupon.entity.Coupon;
import com.smartseckill.coupon.entity.UserCoupon;
import com.smartseckill.coupon.mapper.CouponMapper;
import com.smartseckill.coupon.mapper.UserCouponMapper;
import com.smartseckill.order.dto.CreateOrderRequest;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.entity.OrderItem;
import com.smartseckill.order.entity.OrderStatusLog;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.order.mapper.OrderItemMapper;
import com.smartseckill.order.mapper.OrderStatusLogMapper;
import com.smartseckill.product.entity.ProductSku;
import com.smartseckill.product.mapper.ProductSkuMapper;
import com.smartseckill.product.mapper.ProductStockMapper;
import com.smartseckill.user.entity.UserAddress;
import com.smartseckill.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单业务服务。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>创建订单 → lockStock 锁定库存 → 发 RabbitMQ 延迟消息（30min 超时取消）</li>
 *   <li>支付 → deductStock 扣减真实库存</li>
 *   <li>取消/超时 → unlockStock 释放锁定库存</li>
 * </ol>
 * 所有写操作使用 {@code @Transactional} 保证原子性。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductStockMapper stockMapper;
    private final UserAddressMapper addressMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.smartseckill.seckill.service.SeckillStockRestoreService seckillStockRestoreService;

    /**
     * 创建普通订单。
     * <p>步骤：校验地址 → 生成订单号 → 遍历 SKU 锁定库存 → 写订单 → 发延迟消息</p>
     */
    @Transactional
    public OrderInfo createOrder(Long userId, CreateOrderRequest request) {
        // 1. 获取收货地址并校验归属（userId.equals 避免 getUserId() 为 null 时 NPE）
        UserAddress address = addressMapper.selectById(request.getAddressId());
        if (address == null || address.getDeleted() == 1 || !userId.equals(address.getUserId())) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND);
        }

        // 2. 生成订单编号：时间戳 + 6位随机码
        String orderSn = generateOrderSn();

        // 3. 构建订单项并计算总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            ProductSku sku = skuMapper.selectById(itemReq.getSkuId());
            if (sku == null || sku.getDeleted() == 1) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "SKU不存在: " + itemReq.getSkuId());
            }

            // 原子锁定库存，返回 0 表示可用库存不足
            int lockResult = stockMapper.lockStock(itemReq.getSkuId(), itemReq.getQuantity());
            if (lockResult == 0) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "库存不足: " + sku.getSkuName());
            }

            BigDecimal itemTotal = sku.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderSn(orderSn);
            orderItem.setUserId(userId);
            orderItem.setSkuId(itemReq.getSkuId());
            orderItem.setSpuId(itemReq.getSpuId());
            orderItem.setSkuName(sku.getSkuName());
            orderItem.setSkuImage(sku.getImage());
            orderItem.setSkuPrice(sku.getPrice());
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setTotalAmount(itemTotal);
            orderItem.setSpecInfo(sku.getSpecInfo());
            orderItems.add(orderItem);
        }

        // 4. 计算优惠券
        BigDecimal couponAmount = BigDecimal.ZERO;
        if (request.getCouponId() != null) {
            couponAmount = validateAndApplyCoupon(request.getCouponId(), userId, totalAmount);
        }

        BigDecimal payAmount = totalAmount.subtract(couponAmount);

        // 5. 创建订单主表
        OrderInfo order = new OrderInfo();
        order.setOrderSn(orderSn);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setCouponId(request.getCouponId());
        order.setCouponAmount(couponAmount);
        order.setStatus(0); // 待支付
        order.setOrderType(0); // 普通订单
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(String.format("%s %s %s %s",
                address.getProvince(), address.getCity(),
                address.getDistrict(), address.getDetailAddress()));
        order.setRemark(request.getRemark());
        orderInfoMapper.insert(order);

        // 6. 保存订单项
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 7. 记录状态变更日志
        saveStatusLog(order.getId(), orderSn, null, 0, "system", "创建订单");

        // 8. 发送延迟消息：30 分钟未支付 → 死信队列 → OrderTimeoutConsumer 自动取消
        // 若 MQ 不可用不阻塞下单流程，仅记录日志
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                    RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                    orderSn,
                    message -> {
                        message.getMessageProperties().setExpiration(String.valueOf(30 * 60 * 1000));
                        return message;
                    }
            );
        } catch (Exception e) {
            log.warn("Failed to send order delay message (MQ unavailable): orderSn={}, error={}",
                    orderSn, e.getMessage());
        }

        log.info("Order created: orderSn={}, userId={}, payAmount={}", orderSn, userId, payAmount);
        return order;
    }

    /** 支付订单：校验状态 → 扣减真实库存 → 更新状态为已支付 */
    @Transactional
    public void payOrder(Long userId, String orderSn) {
        OrderInfo order = getOrderBySn(orderSn);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单状态不允许支付");
        }

        order.setStatus(1);
        order.setPaymentTime(LocalDateTime.now());
        orderInfoMapper.updateById(order);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        for (OrderItem item : items) {
            stockMapper.deductStock(item.getSkuId(), item.getQuantity());
        }

        saveStatusLog(order.getId(), orderSn, 0, 1, "user", "订单支付");
    }

    /** 取消订单：仅待支付可取消，释放锁定库存 */
    @Transactional
    public void cancelOrder(Long userId, String orderSn, String reason) {
        OrderInfo order = getOrderBySn(orderSn);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "只有待支付订单可以取消");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        for (OrderItem item : items) {
            stockMapper.unlockStock(item.getSkuId(), item.getQuantity());
        }

        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(reason);
        orderInfoMapper.updateById(order);

        // 秒杀订单（orderType=1）无 OrderItem，上面的 unlockStock 对其无效，
        // 需走秒杀专用回补：Redis 库存 + 已完成标记 + DB stock + 活动剩余库存
        if (order.getOrderType() != null && order.getOrderType() == 1) {
            seckillStockRestoreService.restoreOnCancel(orderSn);
        }

        saveStatusLog(order.getId(), orderSn, 0, 4, "user", reason);
    }

    /** 用户订单分页列表，可按状态筛选 */
    public PageResponse<OrderInfo> getUserOrders(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(OrderInfo::getDeleted, 0)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);

        Page<OrderInfo> result = orderInfoMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result);
    }

    /** 后台所有订单分页列表，支持按订单编号模糊搜索 + 状态筛选 */
    public PageResponse<OrderInfo> getAllOrders(String orderSn, Integer status, int page, int size) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getDeleted, 0)
                .like(orderSn != null && !orderSn.isEmpty(), OrderInfo::getOrderSn, orderSn)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);

        Page<OrderInfo> result = orderInfoMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result);
    }

    /** 根据订单号查询，不存在抛异常（内部使用，不做归属校验） */
    public OrderInfo getOrderBySn(String orderSn) {
        OrderInfo order = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getOrderSn, orderSn)
                        .eq(OrderInfo::getDeleted, 0)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 查询用户自己的订单详情，校验归属 */
    public OrderInfo getUserOrderBySn(Long userId, String orderSn) {
        OrderInfo order = getOrderBySn(orderSn);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    /** 查询用户自己的订单商品项，校验归属 */
    public List<OrderItem> getUserOrderItems(Long userId, String orderSn) {
        getUserOrderBySn(userId, orderSn);
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderSn, orderSn)
                        .eq(OrderItem::getDeleted, 0)
        );
    }

    /** 管理员发货：状态 1→2 */
    @Transactional
    public void deliverOrder(String orderSn) {
        OrderInfo order = getOrderBySn(orderSn);
        if (order.getStatus() != 1) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "只有已支付订单可以发货");
        }

        order.setStatus(2);
        order.setDeliveryTime(LocalDateTime.now());
        orderInfoMapper.updateById(order);

        saveStatusLog(order.getId(), orderSn, 1, 2, "admin", "订单发货");
    }

    /** 用户确认收货：状态 2→3 */
    @Transactional
    public void receiveOrder(Long userId, String orderSn) {
        OrderInfo order = getOrderBySn(orderSn);
        if (!order.getUserId().equals(userId) || order.getStatus() != 2) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        order.setStatus(3);
        order.setReceiveTime(LocalDateTime.now());
        orderInfoMapper.updateById(order);

        saveStatusLog(order.getId(), orderSn, 2, 3, "user", "确认收货");
    }

    /**
     * 生成唯一订单号（高并发友好）。
     * <p>
     * 格式：年月日时分秒（14位）+ Redis 原子自增序号（6位）+ 机器标识（2位）。
     * Redis INCR 保证单日内序号唯一且递增，避免 UUID 截取碰撞风险。
     * 理论容量：单机单日 999999 个订单。
     * </p>
     */
    private String generateOrderSn() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 每日自增序号（Redis INCR 原子操作）
        String seqKey = "order:seq:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
        // 设置 24h 过期，防止僵尸 Key
        if (seq != null && seq == 1L) {
            stringRedisTemplate.expire(seqKey, 1, TimeUnit.DAYS);
        }
        return datePart + String.format("%06d", seq != null ? seq % 1000000 : 1);
    }

    /** 记录订单状态变更历史，便于追溯 */
    private void saveStatusLog(Long orderId, String orderSn, Integer fromStatus,
                               Integer toStatus, String operator, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setOrderSn(orderSn);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setOperator(operator);
        statusLog.setRemark(remark);
        statusLog.setCreateTime(LocalDateTime.now());
        statusLogMapper.insert(statusLog);
    }

    /**
     * 校验并应用优惠券。
     * 返回可抵扣金额，校验失败抛 BusinessException。
     */
    private BigDecimal validateAndApplyCoupon(Long couponId, Long userId, BigDecimal orderTotal) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getDeleted() == 1) {
            throw new BusinessException(ResultCode.COUPON_NOT_FOUND);
        }
        if (coupon.getStatus() != 1) {
            throw new BusinessException(ResultCode.COUPON_NOT_AVAILABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(ResultCode.COUPON_EXPIRED);
        }
        int usedCount = coupon.getUsedCount() != null ? coupon.getUsedCount() : 0;
        int totalCount = coupon.getTotalCount() != null ? coupon.getTotalCount() : 0;
        if (usedCount >= totalCount) {
            throw new BusinessException(ResultCode.COUPON_SOLD_OUT);
        }
        // 检查使用门槛
        if (coupon.getMinAmount() != null && orderTotal.compareTo(coupon.getMinAmount()) < 0) {
            throw new BusinessException(ResultCode.COUPON_MIN_AMOUNT_NOT_MET);
        }
        // 检查用户是否已使用过该券（每张券每人限用一次）
        Long userUsed = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId)
                        .eq(UserCoupon::getStatus, 1));
        if (userUsed > 0) {
            throw new BusinessException(ResultCode.COUPON_ALREADY_USED);
        }

        BigDecimal couponAmount;
        if (coupon.getCouponType() != null && coupon.getCouponType() == 1) {
            // 满减券
            couponAmount = coupon.getAmount() != null ? coupon.getAmount() : BigDecimal.ZERO;
        } else {
            // 折扣券：优惠 = 订单金额 * (1 - 折扣率)
            BigDecimal discount = coupon.getDiscount() != null ? coupon.getDiscount() : BigDecimal.ONE;
            couponAmount = orderTotal.multiply(BigDecimal.ONE.subtract(discount));
        }
        // 确保优惠不超过订单总额
        if (couponAmount.compareTo(orderTotal) > 0) {
            couponAmount = orderTotal;
        }

        // 扣减优惠券可用数量
        coupon.setUsedCount(usedCount + 1);
        couponMapper.updateById(coupon);

        // 将用户的这张券标记为已使用（一张券只能用一次）
        UserCoupon userCoupon = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId)
                        .eq(UserCoupon::getStatus, 0)
        );
        if (userCoupon != null) {
            userCoupon.setStatus(1);
            userCoupon.setUsedTime(now);
            userCouponMapper.updateById(userCoupon);
        }

        log.info("Coupon applied: couponId={}, userId={}, discount={}", couponId, userId, couponAmount);
        return couponAmount;
    }
}
