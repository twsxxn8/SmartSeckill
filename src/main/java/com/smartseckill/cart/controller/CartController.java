package com.smartseckill.cart.controller;

import com.smartseckill.cart.dto.CartItemDTO;
import com.smartseckill.cart.service.CartService;
import com.smartseckill.common.result.Result;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器。
 * <p>
 * 购物车数据存储在 Redis（非数据库），详见 {@link CartService}。
 * 所有接口需登录，通过 LoginUser 获取 userId 隔离不同用户的购物车。
 * </p>
 */
@Tag(name = "购物车管理")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "添加商品到购物车")
    @PostMapping
    public Result<Void> addToCart(@AuthenticationPrincipal LoginUser loginUser,
                                  @Valid @RequestBody CartItemDTO item) {
        cartService.addToCart(loginUser.getUserId(), item);
        return Result.success();
    }

    @Operation(summary = "获取购物车列表")
    @GetMapping
    public Result<List<CartItemDTO>> getCartItems(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(cartService.getCartItems(loginUser.getUserId()));
    }

    @Operation(summary = "更新购物车商品数量")
    @PutMapping("/{skuId}")
    public Result<Void> updateQuantity(@AuthenticationPrincipal LoginUser loginUser,
                                       @PathVariable Long skuId,
                                       @RequestParam Integer quantity) {
        cartService.updateQuantity(loginUser.getUserId(), skuId, quantity);
        return Result.success();
    }

    @Operation(summary = "移除购物车商品")
    @DeleteMapping("/{skuId}")
    public Result<Void> removeFromCart(@AuthenticationPrincipal LoginUser loginUser,
                                       @PathVariable Long skuId) {
        cartService.removeFromCart(loginUser.getUserId(), skuId);
        return Result.success();
    }

    @Operation(summary = "清空购物车")
    @DeleteMapping("/clear")
    public Result<Void> clearCart(@AuthenticationPrincipal LoginUser loginUser) {
        cartService.clearCart(loginUser.getUserId());
        return Result.success();
    }

    @Operation(summary = "获取购物车商品数量")
    @GetMapping("/count")
    public Result<Long> getCartCount(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(cartService.getCartCount(loginUser.getUserId()));
    }
}
