package com.smartseckill.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 机器人运行状态快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotStatus {

    /** 活动 ID */
    private Long activityId;

    /** 是否正在运行 */
    private boolean running;

    /** 配置的机器人总数 */
    private int totalBots;

    /** 已启动的机器人数量 */
    private int botsLaunched;

    /** 已完成的机器人数量（无论成功失败） */
    private int botsCompleted;

    /** 秒杀成功的机器人数 */
    private int botsSucceeded;

    /** 秒杀失败的机器人数（库存不足等） */
    private int botsFailed;

    /** 启动时间戳 */
    private long startTime;

    /** 获取当前活跃的机器人数量 */
    public int getActiveBots() {
        return Math.max(0, botsLaunched - botsCompleted);
    }
}
