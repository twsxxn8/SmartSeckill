package com.smartseckill.seckill.controller;

import com.smartseckill.common.annotation.RateLimit;
import com.smartseckill.common.result.Result;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.seckill.dto.SeckillRequest;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.service.SeckillService;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀控制器。
 * <p>
 * 秒杀链路（用户视角）：
 * <ol>
 *   <li><b>获取 Token</b>：GET /seckill/token?activityId=1 → 拿到防重 token</li>
 *   <li><b>执行秒杀</b>：POST /seckill/execute {activityId, token} → 成功=进入排队/失败</li>
 *   <li><b>查询结果</b>：GET /seckill/result/{activityId} → 是否抢到</li>
 * </ol>
 * </p>
 */
@Tag(name = "秒杀管理")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    // ==================== 前台接口（SecurityConfig 中部分已设为公开） ====================

    @Operation(summary = "获取秒杀活动列表")
    @GetMapping("/list")
    public Result<List<SeckillActivity>> getActiveList() {
        return Result.success(seckillService.getActiveList());
    }

    @Operation(summary = "获取秒杀活动详情")
    @GetMapping("/detail/{activityId}")
    public Result<SeckillActivity> getActivityDetail(@PathVariable Long activityId) {
        return Result.success(seckillService.getActivityDetail(activityId));
    }

    @Operation(summary = "获取秒杀防重Token")
    @GetMapping("/token")
    public Result<Map<String, Object>> getSeckillToken(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam Long activityId) {
        String token = seckillService.generateSeckillToken(loginUser.getUserId(), activityId);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    /**
     * 执行秒杀（核心热路径）。
     * <p>
     * 双重限流：
     * <ul>
     *   <li>本地 Guava RateLimiter：进程级 500 QPS，防止单机被冲垮</li>
     *   <li>Redis 滑动窗口：分布式精确控制，所有实例共享配额</li>
     * </ul>
     * </p>
     */
    @Operation(summary = "执行秒杀")
    @PostMapping("/execute")
    @RateLimit(key = "seckill:execute", permitsPerSecond = 500, dimension = "user",
               message = "秒杀请求过于频繁，请稍后再试")
    public Result<String> executeSeckill(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody SeckillRequest request) {
        seckillService.executeSeckill(loginUser.getUserId(), request);
        // 返回排队中，不直接返回秒杀结果
        return Result.success(ResultCode.SECKILL_QUEUEING.getMessage(), "queuing");
    }

    @Operation(summary = "查询秒杀结果")
    @GetMapping("/result/{activityId}")
    public Result<Map<String, Object>> getSeckillResult(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long activityId) {
        Map<String, Object> result = seckillService.getSeckillResult(loginUser.getUserId(), activityId);
        return Result.success(result);
    }

    @Operation(summary = "获取秒杀活动在线竞争人数")
    @GetMapping("/online-count/{activityId}")
    public Result<Map<String, Object>> getOnlineCount(@PathVariable Long activityId) {
        int count = seckillService.getOnlineCompetitorCount(activityId);
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("onlineCount", count);
        return Result.success(data);
    }

    // ==================== 后台管理接口 ====================

    @Operation(summary = "创建秒杀活动（管理员）")
    @PostMapping("/admin/activity")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createActivity(@RequestBody SeckillActivity activity) {
        seckillService.createActivity(activity);
        return Result.success();
    }

    @Operation(summary = "更新秒杀活动（管理员）")
    @PutMapping("/admin/activity/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateActivity(@PathVariable Long id, @RequestBody SeckillActivity activity) {
        seckillService.updateActivity(id, activity);
        return Result.success();
    }

    @Operation(summary = "删除秒杀活动（管理员）")
    @DeleteMapping("/admin/activity/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        seckillService.deleteActivity(id);
        return Result.success();
    }

    @Operation(summary = "获取全部秒杀活动（管理员）")
    @GetMapping("/admin/activities")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SeckillActivity>> getAllActivities() {
        return Result.success(seckillService.getAllActivities());
    }

    @Operation(summary = "预热秒杀库存到Redis（管理员）")
    @PostMapping("/admin/prewarm/{activityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> preWarmStock(@PathVariable Long activityId) {
        seckillService.preWarmStock(activityId);
        return Result.success();
    }
}
