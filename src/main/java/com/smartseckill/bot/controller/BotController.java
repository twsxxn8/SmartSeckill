package com.smartseckill.bot.controller;

import com.smartseckill.bot.BotConfig;
import com.smartseckill.bot.BotStatus;
import com.smartseckill.bot.service.BotService;
import com.smartseckill.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 机器人军团管理接口。
 * <p>
 * 仅管理员可操作，用于启动/停止虚拟用户参与秒杀竞争。
 * </p>
 */
@Tag(name = "AI 机器人军团管理")
@RestController
@RequestMapping("/seckill/admin/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    @Operation(summary = "启动机器人军团参与秒杀")
    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BotStatus> startBots(@RequestBody Map<String, Object> params) {
        Long activityId = Long.valueOf(params.get("activityId").toString());
        int totalBots = params.containsKey("totalBots") ? (int) params.get("totalBots") : 50;
        int stockAllocation = params.containsKey("stockAllocation") ? (int) params.get("stockAllocation") : totalBots;
        double fastRatio = params.containsKey("fastRatio") ? ((Number) params.get("fastRatio")).doubleValue() : 0.15;
        double normalRatio = params.containsKey("normalRatio") ? ((Number) params.get("normalRatio")).doubleValue() : 0.60;
        double slowRatio = params.containsKey("slowRatio") ? ((Number) params.get("slowRatio")).doubleValue() : 0.25;
        long arrivalWindowMs = params.containsKey("arrivalWindowMs") ? ((Number) params.get("arrivalWindowMs")).longValue() : 3000;

        BotConfig config = BotConfig.builder()
                .totalBots(totalBots)
                .stockAllocation(stockAllocation)
                .fastRatio(fastRatio)
                .normalRatio(normalRatio)
                .slowRatio(slowRatio)
                .arrivalWindowMs(arrivalWindowMs)
                .build();

        BotStatus status = botService.startBotRaid(activityId, config);
        return Result.success(status);
    }

    @Operation(summary = "停止机器人军团")
    @PostMapping("/stop/{activityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BotStatus> stopBots(@PathVariable Long activityId) {
        BotStatus status = botService.stopBotRaid(activityId);
        return Result.success(status);
    }

    @Operation(summary = "查看机器人军团运行状态")
    @GetMapping("/status/{activityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BotStatus> getBotStatus(@PathVariable Long activityId) {
        BotStatus status = botService.getBotStatus(activityId);
        return Result.success(status);
    }
}
