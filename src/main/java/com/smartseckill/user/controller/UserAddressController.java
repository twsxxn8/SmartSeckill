package com.smartseckill.user.controller;

import com.smartseckill.common.result.Result;
import com.smartseckill.user.dto.AddressRequest;
import com.smartseckill.user.entity.UserAddress;
import com.smartseckill.user.security.LoginUser;
import com.smartseckill.user.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户收货地址控制器。
 * <p>
 * 所有接口需登录，通过 {@code @AuthenticationPrincipal LoginUser} 获取当前用户 ID，
 * 确保用户只能操作自己的地址。
 * </p>
 */
@Tag(name = "地址管理")
@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @Operation(summary = "获取地址列表")
    @GetMapping
    public Result<List<UserAddress>> getAddressList(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(userAddressService.getAddressList(loginUser.getUserId()));
    }

    @Operation(summary = "获取地址详情")
    @GetMapping("/{addressId}")
    public Result<UserAddress> getAddress(@AuthenticationPrincipal LoginUser loginUser,
                                          @PathVariable Long addressId) {
        return Result.success(userAddressService.getAddress(loginUser.getUserId(), addressId));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public Result<Void> addAddress(@AuthenticationPrincipal LoginUser loginUser,
                                   @Valid @RequestBody AddressRequest request) {
        userAddressService.addAddress(loginUser.getUserId(), request);
        return Result.success();
    }

    @Operation(summary = "更新地址")
    @PutMapping("/{addressId}")
    public Result<Void> updateAddress(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long addressId,
                                      @Valid @RequestBody AddressRequest request) {
        userAddressService.updateAddress(loginUser.getUserId(), addressId, request);
        return Result.success();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{addressId}")
    public Result<Void> deleteAddress(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long addressId) {
        userAddressService.deleteAddress(loginUser.getUserId(), addressId);
        return Result.success();
    }
}
