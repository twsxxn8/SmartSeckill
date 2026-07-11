package com.smartseckill.seckill.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 秒杀活动状态定时同步器。
 * <p>
 * 问题：SeckillActivity.status 字段（0=未开始, 1=进行中, 2=已结束）
 * 不会自动基于 startTime/endTime 更新，导致用户看到的活动状态与实际时间不一致——
 * 活动已结束但页面仍显示"进行中"或"即将开始"。
 * </p>
 * <p>
 * 本定时任务每分钟运行一次，确保 status 与当前时间保持一致：
 * <ul>
 *   <li>status=0 且 startTime ≤ now 且 endTime > now → status=1（进行中）</li>
 *   <li>status=0/1 且 endTime ≤ now → status=2（已结束）</li>
 *   <li>status=3（已取消）不会被修改</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillActivityScheduler {

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 每分钟同步一次活动状态。
     * 第 7 秒执行，避免与其他整点任务碰撞。
     */
    @Scheduled(cron = "7 * * * * *")
    public void syncActivityStatus() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 未开始 → 进行中：status=0 且 startTime ≤ now 且 endTime > now
        int started = activityMapper.update(null,
                new LambdaUpdateWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 0)
                        .le(SeckillActivity::getStartTime, now)
                        .gt(SeckillActivity::getEndTime, now)
                        .set(SeckillActivity::getStatus, 1)
        );
        if (started > 0) {
            log.info("Synced {} seckill activities: 未开始→进行中", started);
        }

        // 2. 进行中/未开始 → 已结束：status IN (0,1) 且 endTime ≤ now
        int ended = activityMapper.update(null,
                new LambdaUpdateWrapper<SeckillActivity>()
                        .in(SeckillActivity::getStatus, 0, 1)
                        .le(SeckillActivity::getEndTime, now)
                        .set(SeckillActivity::getStatus, 2)
        );
        if (ended > 0) {
            log.info("Synced {} seckill activities: → 已结束", ended);
        }
    }
}
