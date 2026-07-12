package com.smartseckill.coupon.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.Result;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.coupon.dto.UserCouponDTO;
import com.smartseckill.coupon.entity.Coupon;
import com.smartseckill.coupon.entity.UserCoupon;
import com.smartseckill.coupon.mapper.CouponMapper;
import com.smartseckill.coupon.mapper.UserCouponMapper;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "优惠券管理")
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    // ==================== 用户接口 ====================

    /**
     * 领取优惠券。
     * <p>校验：券存在且启用 → 在有效时间窗口内 → 有剩余数量 → 用户未重复领取。</p>
     */
    @Operation(summary = "用户领取优惠券")
    @PostMapping("/{couponId}/claim")
    public Result<Void> claimCoupon(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getDeleted() == 1) {
            return Result.fail(ResultCode.COUPON_NOT_FOUND);
        }
        if (coupon.getStatus() != 1) {
            return Result.fail(ResultCode.COUPON_NOT_AVAILABLE);
        }

        // 校验时间窗口：未开始则拒绝
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            return Result.fail(ResultCode.COUPON_NOT_STARTED);
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            return Result.fail(ResultCode.COUPON_EXPIRED);
        }

        int usedCount = coupon.getUsedCount() != null ? coupon.getUsedCount() : 0;
        int totalCount = coupon.getTotalCount() != null ? coupon.getTotalCount() : 0;
        if (usedCount >= totalCount) {
            return Result.fail(ResultCode.COUPON_SOLD_OUT);
        }

        // 检查是否已领取
        Long count = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, loginUser.getUserId())
                        .eq(UserCoupon::getCouponId, couponId));
        if (count > 0) {
            return Result.fail(ResultCode.COUPON_ALREADY_CLAIMED);
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(loginUser.getUserId());
        uc.setCouponId(couponId);
        uc.setStatus(0);
        userCouponMapper.insert(uc);

        // 更新已发放数量
        coupon.setUsedCount(usedCount + 1);
        couponMapper.updateById(coupon);

        return Result.success();
    }

    /**
     * 查询我的优惠券。
     * <p>
     * 查询前自动将已过期的券标记为 status=2（过期清理）。
     * status 参数：不传=仅未使用(0)，0=未使用，1=已使用，2=已过期。
     * 返回 UserCouponDTO，包含券信息及用户持有状态。
     * </p>
     */
    @Operation(summary = "我的优惠券")
    @GetMapping("/my")
    public Result<List<UserCouponDTO>> getMyCoupons(@AuthenticationPrincipal LoginUser loginUser,
                                                     @RequestParam(required = false) Integer status) {
        // 自动将已过期但未使用的券标记为过期
        autoExpireUserCoupons(loginUser.getUserId());

        // 默认只返回未使用的券
        int filterStatus = (status != null) ? status : 0;

        LambdaQueryWrapper<UserCoupon> ucWrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, loginUser.getUserId())
                .eq(UserCoupon::getStatus, filterStatus);
        List<UserCoupon> userCoupons = userCouponMapper.selectList(ucWrapper);
        if (userCoupons.isEmpty()) return Result.success(new ArrayList<>());

        // 收集 couponId → userStatus 映射
        java.util.Map<Long, Integer> statusMap = new java.util.LinkedHashMap<>();
        for (UserCoupon uc : userCoupons) {
            statusMap.put(uc.getCouponId(), uc.getStatus());
        }

        List<Coupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .in(Coupon::getId, new ArrayList<>(statusMap.keySet()))
                        .eq(Coupon::getDeleted, 0)
                        // 过滤不可用、已过期、已抢光的券，与下单校验保持一致
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getStartTime, LocalDateTime.now())
                        .ge(Coupon::getEndTime, LocalDateTime.now())
                        .apply("total_count > used_count OR total_count IS NULL")
        );

        List<UserCouponDTO> result = new ArrayList<>();
        for (Coupon c : coupons) {
            UserCouponDTO dto = new UserCouponDTO();
            dto.setId(c.getId());
            dto.setCouponName(c.getCouponName());
            dto.setCouponType(c.getCouponType());
            dto.setAmount(c.getAmount());
            dto.setDiscount(c.getDiscount());
            dto.setMinAmount(c.getMinAmount());
            dto.setTotalCount(c.getTotalCount());
            dto.setUsedCount(c.getUsedCount());
            dto.setStartTime(c.getStartTime());
            dto.setEndTime(c.getEndTime());
            dto.setStatus(c.getStatus());
            dto.setCreateTime(c.getCreateTime());
            dto.setUpdateTime(c.getUpdateTime());
            dto.setDeleted(c.getDeleted());
            dto.setUserStatus(statusMap.get(c.getId()));
            result.add(dto);
        }
        return Result.success(result);
    }

    /**
     * 自动将当前用户已过期但状态仍为"未使用"的优惠券标记为"已过期"(status=2)。
     */
    private void autoExpireUserCoupons(Long userId) {
        List<UserCoupon> activeList = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0));
        LocalDateTime now = LocalDateTime.now();
        for (UserCoupon uc : activeList) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null && coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                uc.setStatus(2);
                userCouponMapper.updateById(uc);
            }
        }
    }

    // ==================== 公开接口 ====================

    /**
     * 可领取优惠券列表（领券中心）。
     * <p>只展示启用中、在有效时间窗口内、仍有剩余数量的券。</p>
     */
    @Operation(summary = "可领取优惠券列表（前台）")
    @GetMapping("/available")
    public Result<List<Coupon>> getAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return Result.success(couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .eq(Coupon::getDeleted, 0)
                        .le(Coupon::getStartTime, now)
                        .ge(Coupon::getEndTime, now)
                        .apply("used_count < total_count")
                        .orderByAsc(Coupon::getStartTime)
        ));
    }

    // ==================== 后台管理 ====================

    @Operation(summary = "优惠券列表（管理员）")
    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResponse<Coupon>> adminList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Coupon> result = couponMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getDeleted, 0)
                        .orderByDesc(Coupon::getCreateTime));
        return Result.success(PageResponse.of(result));
    }

    @Operation(summary = "创建优惠券（管理员）")
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody Coupon coupon) {
        couponMapper.insert(coupon);
        return Result.success();
    }

    @Operation(summary = "更新优惠券（管理员）")
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody Coupon coupon) {
        coupon.setId(id);
        couponMapper.updateById(coupon);
        return Result.success();
    }

    @Operation(summary = "删除优惠券（管理员）")
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        couponMapper.deleteById(id);
        return Result.success();
    }
}
