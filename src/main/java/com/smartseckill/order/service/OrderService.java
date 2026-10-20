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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final PlatformTransactionManager transactionManager;
    private final com.smartseckill.seckill.service.SeckillStockRestoreService seckillStockRestoreService;

    /** 下单令牌 Key 前缀：order:submit:token:{userId}:{token}，签发时写入 */
    private static final String ORDER_TOKEN_KEY = "order:submit:token:";
    /** 提交结果 Key 前缀：order:submit:result:{userId}:{token}，SETNX 抢占 + 缓存首单结果 */
    private static final String ORDER_SUBMIT_KEY = "order:submit:result:";
    /** 提交结果占位值：首请求仍在创建中 */
    private static final String SUBMIT_PROCESSING = "PROCESSING";
    /** 下单令牌有效期（分钟） */
    private static final long TOKEN_TTL_MINUTES = 10;
    /** 提交结果缓存时长（小时），覆盖用户重试/页面刷新窗口 */
    private static final long SUBMIT_RESULT_TTL_HOURS = 24;

    /**
     * 创建普通订单（幂等入口）。
     * <p>
     * 防重复提交三道防线：
     * <ol>
     *   <li>一次性令牌：下单前必须先调 {@code POST /order/token} 签发，提交时携带</li>
     *   <li>Redis SETNX 抢占：同一令牌只有第一个请求进入创建流程；重复请求短轮询后
     *       返回首次创建的订单（响应丢失重试场景用户无感，不会产生新订单）</li>
     *   <li>DB 唯一索引兜底：order_info.submit_token 过滤唯一索引，Redis 失效时
     *       并发第二笔 insert 冲突，转为返回已存在的订单</li>
     * </ol>
     * 事务用 {@link TransactionTemplate} 编程式控制（同类自调用不走 Spring 代理），
     * 保证「先提交事务、后写结果缓存」与「先回滚、后释放锁」的顺序。
     * </p>
     */
    public OrderInfo createOrder(Long userId, CreateOrderRequest request) {
        String token = request.getSubmitToken();
        String tokenKey = ORDER_TOKEN_KEY + userId + ":" + token;
        String submitKey = ORDER_SUBMIT_KEY + userId + ":" + token;

        // 1. 先查结果缓存：该令牌已成功创建过订单 → 直接返回首单
        //    （必须在令牌校验之前：成功后令牌已销毁，否则会误判 1204）
        String cached = stringRedisTemplate.opsForValue().get(submitKey);
        if (cached != null && !SUBMIT_PROCESSING.equals(cached)) {
            return getOrderBySn(cached);
        }

        // 2. 校验令牌已签发且未过期
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
            throw new BusinessException(ResultCode.ORDER_TOKEN_INVALID);
        }

        // 3. SETNX 抢占：仅第一个请求继续创建，重复请求复用首单结果
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(submitKey, SUBMIT_PROCESSING, SUBMIT_RESULT_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(first)) {
            return waitForCreatedOrder(submitKey);
        }

        try {
            OrderInfo order = new TransactionTemplate(transactionManager)
                    .execute(status -> doCreateOrder(userId, request));
            // 3. 创建成功：写入结果供重复请求复用，并消费一次性令牌
            stringRedisTemplate.opsForValue().set(
                    submitKey, order.getOrderSn(), SUBMIT_RESULT_TTL_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.delete(tokenKey);
            return order;
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底命中：并发穿透 Redis 时被 DB 拦截，返回已创建的订单
            OrderInfo existing = getOrderBySubmitToken(userId, token);
            if (existing != null) {
                stringRedisTemplate.opsForValue().set(
                        submitKey, existing.getOrderSn(), SUBMIT_RESULT_TTL_HOURS, TimeUnit.HOURS);
                stringRedisTemplate.delete(tokenKey);
                return existing;
            }
            // 首单尚未提交（极小窗口）：释放锁让客户端重试
            stringRedisTemplate.delete(submitKey);
            throw e;
        } catch (Exception e) {
            // 创建失败（库存不足等）：释放提交锁，允许用户修正后重试
            stringRedisTemplate.delete(submitKey);
            throw e;
        }
    }

    /**
     * 重复提交处理：首请求仍在创建中，短轮询等待其写入结果（最长约 3s）后返回同一笔订单；
     * 超时则提示用户到订单列表确认，而不是报错丢单。
     */
    private OrderInfo waitForCreatedOrder(String submitKey) {
        for (int i = 0; i < 15; i++) {
            String orderSn = stringRedisTemplate.opsForValue().get(submitKey);
            if (orderSn != null && !SUBMIT_PROCESSING.equals(orderSn)) {
                return getOrderBySn(orderSn);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException(ResultCode.ORDER_SUBMITTING);
    }

    /** 签发一次性下单令牌（进入确认订单页时调用），10 分钟有效，下单成功后销毁 */
    public String generateSubmitToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                ORDER_TOKEN_KEY + userId + ":" + token, "1", TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    /** 按提交令牌查询订单（submit_token 唯一索引保证最多一条） */
    private OrderInfo getOrderBySubmitToken(Long userId, String submitToken) {
        return orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getSubmitToken, submitToken)
                .eq(OrderInfo::getUserId, userId));
    }

    /**
     * 实际创建订单。
     * <p>步骤：校验地址 → 生成订单号 → 遍历 SKU 锁定库存 → 写订单 → 发延迟消息</p>
     * <p>由 {@link #createOrder} 通过 {@link TransactionTemplate} 包裹在事务内调用。</p>
     */
    private OrderInfo doCreateOrder(Long userId, CreateOrderRequest request) {
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
        order.setSubmitToken(request.getSubmitToken()); // 幂等令牌落库，唯一索引兜底防重
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
