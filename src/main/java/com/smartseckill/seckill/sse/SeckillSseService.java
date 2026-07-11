package com.smartseckill.seckill.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 实时推送服务，管理秒杀活动频道的客户端连接与事件广播。
 * <p>
 * 每个活动 ID 对应一个频道，客户端通过 {@code GET /seckill/sse/{activityId}} 订阅。
 * 服务端通过 {@link #emit(Long, String, Object)} 向指定活动的所有订阅者广播事件。
 * </p>
 */
@Slf4j
@Service
public class SeckillSseService {

    /**
     * activityId → 该活动下所有活跃的 SseEmitter 列表。
     * CopyOnWriteArrayList 保证遍历广播时的线程安全（广播频率远高于订阅/取消订阅）。
     */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> channels = new ConcurrentHashMap<>();

    /**
     * emitter → userId 映射，用于 emitToUser 定向推送。
     * 在 subscribe 时写入，在 removeEmitter 时清理。
     */
    private final Map<SseEmitter, Long> emitterUserIdMap = new ConcurrentHashMap<>();

    /** SSE 超时时间（毫秒），超时后客户端自动重连 */
    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 分钟

    /**
     * 订阅指定秒杀活动的实时频道。
     *
     * @param activityId 秒杀活动 ID
     * @param userId     用户 ID（用于定向推送个人秒杀结果）
     * @return SseEmitter 实例，由 Spring MVC 异步管理
     */
    public SseEmitter subscribe(Long activityId, Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CopyOnWriteArrayList<SseEmitter> activityEmitters = channels.computeIfAbsent(
                activityId, k -> new CopyOnWriteArrayList<>());

        activityEmitters.add(emitter);
        emitterUserIdMap.put(emitter, userId);

        // 注册完成/超时/错误回调，及时清理
        emitter.onCompletion(() -> removeEmitter(activityId, emitter));
        emitter.onTimeout(() -> removeEmitter(activityId, emitter));
        emitter.onError(e -> {
            log.debug("SSE error for userId={}, activityId={}: {}", userId, activityId, e.getMessage());
            removeEmitter(activityId, emitter);
        });

        // 发送初始连接确认事件
        sendEvent(emitter, "connected", Map.of("userId", userId, "message", "已连接实时频道"));

        log.debug("SSE subscribed: activityId={}, userId={}, channelSize={}",
                activityId, userId, activityEmitters.size());
        return emitter;
    }

    /**
     * 向指定秒杀活动的所有订阅者广播事件。
     *
     * @param activityId  秒杀活动 ID
     * @param eventName   事件名称（如 stock_changed、winner_announcement）
     * @param eventData   事件数据（会被序列化为 JSON）
     */
    public void emit(Long activityId, String eventName, Object eventData) {
        CopyOnWriteArrayList<SseEmitter> emitters = channels.get(activityId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        int failed = 0;
        for (SseEmitter emitter : emitters) {
            if (!sendEvent(emitter, eventName, eventData)) {
                failed++;
            }
        }

        if (failed > 0) {
            log.debug("SSE broadcast: activityId={}, event={}, total={}, failed={}",
                    activityId, eventName, emitters.size(), failed);
        }
    }

    /**
     * 向指定用户发送个人事件（定向推送，如秒杀结果）。
     * <p>
     * 通过 emitterUserIdMap 过滤，只推送给目标用户，不会广播给其他用户。
     * </p>
     */
    public void emitToUser(Long activityId, Long userId, String eventName, Object eventData) {
        CopyOnWriteArrayList<SseEmitter> emitters = channels.get(activityId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            Long mappedUserId = emitterUserIdMap.get(emitter);
            if (mappedUserId != null && mappedUserId.equals(userId)) {
                sendEvent(emitter, eventName, eventData);
            }
        }
    }

    /** 获取指定活动的在线订阅人数 */
    public int getSubscriberCount(Long activityId) {
        CopyOnWriteArrayList<SseEmitter> emitters = channels.get(activityId);
        return emitters != null ? emitters.size() : 0;
    }

    // ==================== 私有方法 ====================

    private boolean sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            return true;
        } catch (IOException e) {
            // 客户端断开连接，emitter 会在 onError/onCompletion 回调中清理
            return false;
        }
    }

    private void removeEmitter(Long activityId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = channels.get(activityId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                channels.remove(activityId);
            }
        }
        emitterUserIdMap.remove(emitter);
    }
}
