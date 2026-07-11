package com.smartseckill.seckill.controller;

import com.smartseckill.seckill.sse.SeckillSseService;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 实时推送控制器。
 * <p>
 * 前端通过 EventSource 连接此端点，接收秒杀活动的实时事件推送。
 * 支持的事件类型：
 * <ul>
 *   <li>{@code connected} — 连接成功确认</li>
 *   <li>{@code stock_changed} — 库存变化（剩余数量）</li>
 *   <li>{@code competitor_joined} — 有用户参与竞争（竞争人数）</li>
 *   <li>{@code winner_announcement} — 有人成功秒杀（脱敏用户名）</li>
 *   <li>{@code seckill_result} — 个人秒杀结果（success/failed/queuing）</li>
 * </ul>
 * </p>
 */
@Tag(name = "秒杀实时推送")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillSseController {

    private final SeckillSseService sseService;

    /**
     * 订阅秒杀活动实时频道。
     * <p>
     * 前端使用 EventSource 连接，默认自动重连。
     * 推流持续到超时（5 分钟），超时后前端自动重连。
     * </p>
     *
     * @param loginUser  当前登录用户
     * @param activityId 秒杀活动 ID
     * @return SseEmitter 实例
     */
    @Operation(summary = "订阅秒杀活动实时频道（SSE）")
    @GetMapping(value = "/sse/{activityId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long activityId) {
        return sseService.subscribe(activityId, loginUser.getUserId());
    }
}
