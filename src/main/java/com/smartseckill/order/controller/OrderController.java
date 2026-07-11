package com.smartseckill.order.controller;

import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.Result;
import com.smartseckill.order.dto.CreateOrderRequest;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.entity.OrderItem;
import com.smartseckill.order.service.OrderService;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单控制器。
 * <p>
 * 订单状态流转：待支付(0) → 已支付(1) → 已发货(2) → 已完成(3) / 已取消(4)
 * </p>
 */
@Tag(name = "订单管理")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单：锁定库存 + 发送 30 分钟超时取消消息 */
    @Operation(summary = "创建订单")
    @PostMapping
    public Result<OrderInfo> createOrder(@AuthenticationPrincipal LoginUser loginUser,
                                         @Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.createOrder(loginUser.getUserId(), request));
    }

    /** 支付订单：扣减真实库存，状态 0→1 */
    @Operation(summary = "支付订单")
    @PostMapping("/{orderSn}/pay")
    public Result<Void> payOrder(@AuthenticationPrincipal LoginUser loginUser,
                                 @PathVariable String orderSn) {
        orderService.payOrder(loginUser.getUserId(), orderSn);
        return Result.success();
    }

    /** 用户主动取消：仅待支付订单可取消，释放锁定库存 */
    @Operation(summary = "取消订单")
    @PostMapping("/{orderSn}/cancel")
    public Result<Void> cancelOrder(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable String orderSn,
                                    @RequestBody Map<String, String> request) {
        orderService.cancelOrder(loginUser.getUserId(), orderSn, request.get("reason"));
        return Result.success();
    }

    /** 确认收货：状态 2→3 */
    @Operation(summary = "确认收货")
    @PostMapping("/{orderSn}/receive")
    public Result<Void> receiveOrder(@AuthenticationPrincipal LoginUser loginUser,
                                     @PathVariable String orderSn) {
        orderService.receiveOrder(loginUser.getUserId(), orderSn);
        return Result.success();
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/my")
    public Result<PageResponse<OrderInfo>> getMyOrders(@AuthenticationPrincipal LoginUser loginUser,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.getUserOrders(loginUser.getUserId(), status, page, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderSn}")
    public Result<OrderInfo> getOrderDetail(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable String orderSn) {
        return Result.success(orderService.getUserOrderBySn(loginUser.getUserId(), orderSn));
    }

    @Operation(summary = "订单项列表")
    @GetMapping("/{orderSn}/items")
    public Result<List<OrderItem>> getOrderItems(@AuthenticationPrincipal LoginUser loginUser,
                                                 @PathVariable String orderSn) {
        return Result.success(orderService.getUserOrderItems(loginUser.getUserId(), orderSn));
    }

    // ==================== 后台管理 ====================

    @Operation(summary = "所有订单列表（管理员）")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResponse<OrderInfo>> getAllOrders(@RequestParam(required = false) String orderSn,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.getAllOrders(orderSn, status, page, size));
    }

    @Operation(summary = "订单发货（管理员）")
    @PostMapping("/admin/{orderSn}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deliverOrder(@PathVariable String orderSn) {
        orderService.deliverOrder(orderSn);
        return Result.success();
    }
}
