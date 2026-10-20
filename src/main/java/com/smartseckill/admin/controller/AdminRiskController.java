package com.smartseckill.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.Result;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.risk.entity.RiskDecisionLog;
import com.smartseckill.risk.mapper.RiskDecisionLogMapper;
import com.smartseckill.risk.service.RiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理后台-风控管理控制器。
 * <p>提供风控决策日志审计查询与黑名单管理功能（仅 ADMIN 角色）。</p>
 */
@Tag(name = "管理后台-风控管理")
@RestController
@RequestMapping("/admin/risk")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminRiskController {

    private final RiskDecisionLogMapper riskDecisionLogMapper;
    private final RiskControlService riskControlService;
    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "风控决策日志（分页，按时间倒序）")
    @GetMapping("/decisions")
    public Result<PageResponse<RiskDecisionLog>> decisions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        LambdaQueryWrapper<RiskDecisionLog> wrapper = new LambdaQueryWrapper<RiskDecisionLog>()
                .eq(userId != null, RiskDecisionLog::getUserId, userId)
                .eq(activityId != null, RiskDecisionLog::getActivityId, activityId)
                .eq(StringUtils.hasText(action), RiskDecisionLog::getAction, action)
                .ge(startTime != null, RiskDecisionLog::getCreatedAt, startTime)
                .le(endTime != null, RiskDecisionLog::getCreatedAt, endTime)
                .orderByDesc(RiskDecisionLog::getCreatedAt);
        Page<RiskDecisionLog> result = riskDecisionLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(PageResponse.of(result));
    }

    @Operation(summary = "黑名单列表（SCAN 遍历 Redis）")
    @GetMapping("/blacklist")
    public Result<List<Long>> blacklist() {
        // SCAN 非阻塞遍历黑名单 Key，提取 userId 列表
        Set<Long> userIds = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisUtil.RISK_BLACKLIST_PREFIX + "*")
                .count(1000)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    userIds.add(Long.parseLong(
                            key.substring(RedisUtil.RISK_BLACKLIST_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // 忽略非数字后缀的脏 Key
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan risk blacklist: {}", e.getMessage());
        }
        return Result.success(new ArrayList<>(userIds));
    }

    @Operation(summary = "手动拉黑用户")
    @PostMapping("/blacklist/{userId}")
    public Result<Void> addBlacklist(@PathVariable Long userId) {
        riskControlService.addToBlacklist(userId);
        return Result.success();
    }

    @Operation(summary = "解除用户拉黑")
    @DeleteMapping("/blacklist/{userId}")
    public Result<Void> removeBlacklist(@PathVariable Long userId) {
        riskControlService.removeFromBlacklist(userId);
        return Result.success();
    }
}
