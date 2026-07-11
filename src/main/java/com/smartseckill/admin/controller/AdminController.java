package com.smartseckill.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.result.Result;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.product.entity.ProductSpu;
import com.smartseckill.product.mapper.ProductSpuMapper;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.user.entity.SysUser;
import com.smartseckill.user.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理后台-仪表盘")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final SysUserMapper userMapper;
    private final ProductSpuMapper spuMapper;
    private final OrderInfoMapper orderMapper;
    private final SeckillActivityMapper activityMapper;

    @Operation(summary = "仪表盘统计数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("activeUsers", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1).eq(SysUser::getDeleted, 0)));
        stats.put("todayNewUsers", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().ge(SysUser::getCreateTime, today).eq(SysUser::getDeleted, 0)));

        stats.put("totalProducts", spuMapper.selectCount(null));
        stats.put("publishedProducts", spuMapper.selectCount(
                new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getStatus, 1).eq(ProductSpu::getDeleted, 0)));

        stats.put("totalOrders", orderMapper.selectCount(null));
        stats.put("pendingOrders", orderMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 0).eq(OrderInfo::getDeleted, 0)));
        stats.put("paidOrders", orderMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 1).eq(OrderInfo::getDeleted, 0)));
        stats.put("deliveredOrders", orderMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 2).eq(OrderInfo::getDeleted, 0)));
        stats.put("todayOrders", orderMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>().ge(OrderInfo::getCreateTime, today).eq(OrderInfo::getDeleted, 0)));

        stats.put("activeSeckills", activityMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivity>().eq(SeckillActivity::getStatus, 1).eq(SeckillActivity::getDeleted, 0)));

        return Result.success(stats);
    }
}
