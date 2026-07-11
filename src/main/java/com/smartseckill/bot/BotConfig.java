package com.smartseckill.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 机器人配置。
 * <p>
 * 定义每个活动的虚拟用户军团参数。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotConfig {

    /** 机器人总数 */
    private int totalBots;

    /** 分配给机器人的库存数量（机器人消耗此配额） */
    private int stockAllocation;

    /** 快速反应机器人占比（30-80ms 反应时间），默认 15% */
    @Builder.Default
    private double fastRatio = 0.15;

    /** 正常反应机器人占比（80-300ms），默认 60% */
    @Builder.Default
    private double normalRatio = 0.60;

    /** 慢速反应机器人占比（300-800ms），默认 25% */
    @Builder.Default
    private double slowRatio = 0.25;

    /** 活动开始后机器人到达的时间跨度（毫秒），默认 3000ms */
    @Builder.Default
    private long arrivalWindowMs = 3000;

    /** 机器人成功抢到后支付概率（0-1），默认 0.8 */
    @Builder.Default
    private double payProbability = 0.8;
}
