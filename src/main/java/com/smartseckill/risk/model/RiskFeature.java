package com.smartseckill.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户行为特征快照，从 Redis 中实时提取，供 AI 风控评分引擎使用。
 * <p>
 * 特征分为以下几类：
 * <ul>
 *   <li>频率特征：请求间隔、请求次数</li>
 *   <li>速度特征：Token 获取到执行的时间间隔（过快=脚本行为）</li>
 *   <li>模式特征：IP 变化、UA 变化、活动跳跃</li>
 *   <li>历史特征：用户注册时间、历史秒杀成功率</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFeature {

    // ==================== 频率特征 ====================
    /** 当前窗口内的秒杀请求次数 */
    private int requestCount;
    /** 平均请求间隔（毫秒），请求次数不足 2 次时为 -1 */
    private double avgIntervalMs;
    /** 最小请求间隔（毫秒），请求次数不足 2 次时为 -1 */
    private double minIntervalMs;

    // ==================== 速度特征 ====================
    /** Token 获取到秒杀执行的间隔（毫秒），值越小越可疑 */
    private long tokenToExecuteMs;

    // ==================== 模式特征 ====================
    /** 同一 IP 在窗口内关联的账号数 */
    private int ipAccountCount;
    /** 是否切换过 User-Agent（同一用户多次请求） */
    private boolean uaSwitched;
    /** 窗口内切换过的活动数量 */
    private int activitySwitchCount;

    // ==================== 历史特征 ====================
    /** 用户注册距今的天数 */
    private long accountAgeDays;
    /** 历史秒杀成功率（0.0 ~ 1.0） */
    private double historicalSuccessRate;
    /** 是否新注册（7 天以内） */
    private boolean isNewAccount;

    // ==================== 辅助方法 ====================

    /**
     * 判断请求速度是否达到脚本级别。
     * 人类最快反应时间约 100-150ms（专业电竞选手约 150ms），
     * Token→执行间隔低于 80ms 基本可以判定为脚本。
     */
    public boolean isInhumanSpeed() {
        return minIntervalMs > 0 && minIntervalMs < 80;
    }
}
