package com.smartseckill.ai.controller;

import com.smartseckill.ai.service.SeckillAssistantService;
import com.smartseckill.common.result.Result;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 秒杀助手接口。
 * <p>
 * 提供秒杀成功率预测、策略建议、活动热度分析等功能。
 * </p>
 */
@Tag(name = "AI 秒杀助手")
@RestController
@RequestMapping("/seckill/ai")
@RequiredArgsConstructor
public class SeckillAssistantController {

    private final SeckillAssistantService assistantService;

    @Operation(summary = "预测秒杀成功率")
    @GetMapping("/predict/{activityId}")
    public Result<Map<String, Object>> predictSuccessRate(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long activityId) {
        Map<String, Object> result = assistantService.predictSuccessRate(activityId, loginUser.getUserId());
        return Result.success(result);
    }

    @Operation(summary = "获取个人的秒杀行为分析与建议")
    @GetMapping("/analysis/me")
    public Result<Map<String, Object>> getUserAnalysis(
            @AuthenticationPrincipal LoginUser loginUser) {
        Map<String, Object> analysis = assistantService.getUserAnalysis(loginUser.getUserId());
        return Result.success(analysis);
    }

    @Operation(summary = "获取活动热度分析")
    @GetMapping("/heat/{activityId}")
    public Result<Map<String, Object>> getActivityHeat(
            @PathVariable Long activityId) {
        Map<String, Object> heat = assistantService.getActivityHeatAnalysis(activityId);
        return Result.success(heat);
    }
}
