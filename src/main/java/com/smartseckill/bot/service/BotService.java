package com.smartseckill.bot.service;

import com.smartseckill.bot.BotConfig;
import com.smartseckill.bot.BotSpeed;
import com.smartseckill.bot.BotStatus;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.common.util.SeckillTokenUtil;
import com.smartseckill.seckill.dto.SeckillRequest;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.service.SeckillService;
import com.smartseckill.seckill.sse.SeckillEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 秒杀机器人军团服务。
 * <p>
 * 为每个秒杀活动创建一组虚拟用户，模拟真实用户行为参与秒杀竞争。
 * 机器人走完整的秒杀流程（Token→执行），消耗真实库存配额，
 * 为用户提供真实的竞争体验和高并发的视觉氛围。
 * </p>
 * <p>
 * <b>使用方式：</b>
 * <ol>
 *   <li>管理员调用 startBotRaid 启动机器人军团</li>
 *   <li>机器人按泊松分布到达时间参与秒杀</li>
 *   <li>机器人结果通过 SSE 推送（中奖跑马灯效果）</li>
 *   <li>管理员可随时停止、查看状态</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final SeckillService seckillService;
    private final SeckillTokenUtil seckillTokenUtil;
    private final SeckillActivityMapper activityMapper;
    private final SeckillEventPublisher eventPublisher;

    /**
     * 正在运行的机器人会话：activityId → BotStatus
     */
    private final Map<Long, BotStatus> runningSessions = new ConcurrentHashMap<>();

    /**
     * 是否应该停止：activityId → boolean
     */
    private final Map<Long, Boolean> stopFlags = new ConcurrentHashMap<>();

    private final Random random = new Random();

    /** 机器人用户 ID 起始值（避免与真实用户冲突） */
    private static final long BOT_ID_BASE = 900000L;

    /**
     * 启动 AI 机器人军团参与秒杀。
     *
     * @param activityId 秒杀活动 ID
     * @param config     机器人配置（数量、速度分布、到达时间窗口）
     * @return 运行状态
     */
    public BotStatus startBotRaid(Long activityId, BotConfig config) {
        // 校验活动存在且有效
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        if (activity.getStatus() != 1) {
            throw new BusinessException(ResultCode.SECKILL_ALREADY_ENDED, "活动未在进行中");
        }

        // 检查是否已在运行
        if (runningSessions.containsKey(activityId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该活动的机器人军团已在运行中");
        }

        int actualBotCount = Math.min(config.getTotalBots(), Math.max(1, activity.getAvailableStock() / 2));

        BotStatus status = BotStatus.builder()
                .activityId(activityId)
                .running(true)
                .totalBots(actualBotCount)
                .botsLaunched(0)
                .botsCompleted(0)
                .botsSucceeded(0)
                .botsFailed(0)
                .startTime(System.currentTimeMillis())
                .build();

        runningSessions.put(activityId, status);
        stopFlags.put(activityId, false);

        // 异步启动机器人军团
        launchBotArmy(activityId, config, actualBotCount);

        log.info("Bot raid started: activityId={}, totalBots={}", activityId, actualBotCount);
        return status;
    }

    /**
     * 停止机器人军团。
     */
    public BotStatus stopBotRaid(Long activityId) {
        stopFlags.put(activityId, true);

        BotStatus status = runningSessions.get(activityId);
        if (status != null) {
            status.setRunning(false);
        }

        log.info("Bot raid stopped: activityId={}", activityId);
        return status != null ? status : BotStatus.builder().activityId(activityId).running(false).build();
    }

    /**
     * 获取机器人运行状态。
     */
    public BotStatus getBotStatus(Long activityId) {
        BotStatus status = runningSessions.get(activityId);
        if (status == null) {
            return BotStatus.builder().activityId(activityId).running(false).build();
        }
        return status;
    }

    // ==================== 私有方法 ====================

    @Async
    void launchBotArmy(Long activityId, BotConfig config, int actualBotCount) {
        AtomicInteger launched = new AtomicInteger(0);
        BotStatus status = runningSessions.get(activityId);

        for (int i = 0; i < actualBotCount; i++) {
            if (Boolean.TRUE.equals(stopFlags.get(activityId))) {
                break;
            }

            long botUserId = BOT_ID_BASE + activityId * 10000 + i;

            // 泊松分布到达时间：在 arrivalWindowMs 窗口内随机分布
            long arrivalDelay = (long) (Math.abs(random.nextGaussian()) * config.getArrivalWindowMs() / 3);
            arrivalDelay = Math.min(arrivalDelay, config.getArrivalWindowMs());

            // 确定机器人的速度类型
            BotSpeed speed = assignBotSpeed(config);

            // 异步启动机器人
            launchBot(activityId, botUserId, speed, arrivalDelay, status);

            launched.incrementAndGet();
            if (status != null) {
                status.setBotsLaunched(launched.get());
            }
        }

        log.info("All bots launched: activityId={}, count={}", activityId, launched.get());
    }

    @Async
    void launchBot(Long activityId, long botUserId, BotSpeed speed, long arrivalDelay, BotStatus status) {
        try {
            // 1. 等待到达时间
            Thread.sleep(arrivalDelay);

            if (Boolean.TRUE.equals(stopFlags.get(activityId))) {
                return;
            }

            // 2. 生成 Token（机器人仍需要 Token）
            String token = seckillTokenUtil.generateToken(botUserId, activityId);

            // 3. 模拟人的反应时间（依速度类型而定）
            long reactionTime = getReactionTime(speed);
            Thread.sleep(reactionTime);

            // 4. 执行秒杀
            try {
                seckillService.executeSeckill(botUserId, new SeckillRequest(activityId, token));

                // 5. 秒杀成功 → 推送中奖公告
                if (status != null) {
                    status.setBotsSucceeded(status.getBotsSucceeded() + 1);
                }
                eventPublisher.publishWinnerAnnouncement(activityId, "用户" + botUserId);

                log.debug("Bot succeeded: activityId={}, botUserId={}, speed={}", activityId, botUserId, speed);
            } catch (BusinessException e) {
                // 秒杀失败（库存不足、风控等）
                if (status != null) {
                    status.setBotsFailed(status.getBotsFailed() + 1);
                }
                log.debug("Bot failed: activityId={}, botUserId={}, reason={}", activityId, botUserId, e.getMessage());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (status != null) {
                status.setBotsCompleted(status.getBotsCompleted() + 1);
            }

            // 所有机器人完成时自动清理
            if (status != null && status.getBotsCompleted() >= status.getTotalBots()) {
                status.setRunning(false);
                runningSessions.remove(activityId);
                stopFlags.remove(activityId);
                log.info("Bot raid completed: activityId={}, succeeded={}, failed={}",
                        activityId, status.getBotsSucceeded(), status.getBotsFailed());
            }
        }
    }

    /** 根据配置中的比例随机分配机器人速度类型 */
    private BotSpeed assignBotSpeed(BotConfig config) {
        double roll = random.nextDouble();
        if (roll < config.getFastRatio()) {
            return BotSpeed.FAST;
        } else if (roll < config.getFastRatio() + config.getNormalRatio()) {
            return BotSpeed.NORMAL;
        } else {
            return BotSpeed.SLOW;
        }
    }

    /** 获取指定速度类型的反应时间（毫秒） */
    private long getReactionTime(BotSpeed speed) {
        switch (speed) {
            case FAST:
                return 30 + random.nextInt(70);   // 30-100ms
            case NORMAL:
                return 100 + random.nextInt(200);  // 100-300ms
            case SLOW:
                return 300 + random.nextInt(500);  // 300-800ms
            default:
                return 100 + random.nextInt(200);
        }
    }
}
