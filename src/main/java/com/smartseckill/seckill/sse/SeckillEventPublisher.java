package com.smartseckill.seckill.sse;

import com.smartseckill.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀事件发布器，在秒杀流程关键节点发布事件到 SSE 频道。
 * <p>
 * 职责：
 * <ul>
 *   <li>库存变化时广播 stock_changed</li>
 *   <li>秒杀成功时广播 winner_announcement（脱敏） + 个人 seckill_result</li>
 *   <li>用户参与时广播 competitor_joined</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillEventPublisher {

    private final SeckillSseService sseService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发布库存变化事件。
     * <p>
     * 在 Redis 库存预减成功后调用，通知所有订阅者更新剩余库存显示。
     * </p>
     */
    public void publishStockChanged(Long activityId) {
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);

        int remaining = 0;
        if (stockStr != null) {
            try {
                remaining = Integer.parseInt(stockStr);
            } catch (NumberFormatException ignored) {
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("remainingStock", Math.max(0, remaining));

        sseService.emit(activityId, "stock_changed", data);
    }

    /**
     * 发布用户参与事件（竞争计数更新）。
     * <p>
     * 每次有用户成功进入秒杀队列时调用。
     * </p>
     */
    public void publishCompetitorJoined(Long activityId, int competitorCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("competitorCount", competitorCount);

        sseService.emit(activityId, "competitor_joined", data);
    }

    /**
     * 发布中奖公告（有人成功秒杀到）。
     * <p>
     * 对用户名做脱敏处理：保留首尾字符，中间用 *** 替换。
     * 同时更新 Redis 中奖列表（最近 N 条）。
     * </p>
     *
     * @param activityId 活动 ID
     * @param username   原始用户名（会自动脱敏）
     */
    public void publishWinnerAnnouncement(Long activityId, String username) {
        String maskedName = maskUsername(username);

        // 写入 Redis 中奖列表（最近 20 条）
        String winnerKey = RedisUtil.SECKILL_WINNER_PREFIX + activityId;
        stringRedisTemplate.opsForList().leftPush(winnerKey, maskedName);
        stringRedisTemplate.opsForList().trim(winnerKey, 0, (int) RedisUtil.SECKILL_WINNER_MAX_SIZE - 1);
        stringRedisTemplate.expire(winnerKey, 1, TimeUnit.HOURS);

        Map<String, Object> data = new HashMap<>();
        data.put("username", maskedName);
        data.put("message", maskedName + " 刚刚抢到了！");

        sseService.emit(activityId, "winner_announcement", data);

        log.debug("Winner announced: activityId={}, user={}", activityId, maskedName);
    }

    /**
     * 向指定用户发布个人秒杀结果。
     * <p>
     * 在秒杀失败（库存不足/重复/风控拦截等）时立即推送；
     * 秒杀成功时由 MQ 消费者异步推送。
     * </p>
     */
    public void publishSeckillResult(Long activityId, Long userId, String status, String message, String orderSn) {
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("userId", userId);
        data.put("status", status);    // "success" | "failed" | "queuing"
        data.put("message", message);
        if (orderSn != null) {
            data.put("orderSn", orderSn);
        }

        sseService.emitToUser(activityId, userId, "seckill_result", data);
    }

    // ==================== 私有方法 ====================

    /**
     * 用户名脱敏：保留首字符和尾字符，中间用 *** 替换。
     * 2 字：王* / 3 字：张*三 / 4+ 字：张**四
     */
    private String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "***";
        }
        if (username.length() == 1) {
            return username;
        }
        if (username.length() == 2) {
            return username.charAt(0) + "*";
        }
        // length >= 3
        int maskLen = Math.min(username.length() - 2, 3);
        return username.charAt(0) + "*".repeat(maskLen) + username.charAt(username.length() - 1);
    }
}
