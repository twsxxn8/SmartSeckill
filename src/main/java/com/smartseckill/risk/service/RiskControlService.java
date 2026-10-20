package com.smartseckill.risk.service;

import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.risk.model.RiskDecisionEvent;
import com.smartseckill.risk.model.RiskFeature;
import com.smartseckill.risk.model.RiskDecision;
import com.smartseckill.risk.model.RiskDecision.RiskLevel;
import com.smartseckill.risk.model.RiskDecision.RiskAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能风控评分引擎。
 * <p>
 * 采用规则引擎 + 加权评分模型，从 Redis 中提取用户行为特征，
 * 对每次秒杀请求进行实时风险评估。
 * </p>
 * <p>
 * <b>评分体系</b>（总分 100 分，越高越可疑）：
 * <ul>
 *   <li>请求频率过高（>10次/秒）：+20</li>
 *   <li>请求间隔过短（<80ms）：+40（脚本行为）</li>
 *   <li>Token→执行过快（<100ms）：+25（无人工操作）</li>
 *   <li>同一 IP 多账号（≥3）：+30</li>
 *   <li>UA 频繁变化：+20</li>
 *   <li>快速切换活动（≥3）：+15</li>
 *   <li>新注册账号（<7天）：+10</li>
 *   <li>历史秒杀成功率异常高（>80%）：+15</li>
 * </ul>
 * </p>
 * <p>
 * <b>处置阈值</b>：
 * <ul>
 *   <li>0-29：PASS（放行）</li>
 *   <li>30-49：DELAY（延迟 100-300ms）</li>
 *   <li>50-79：VERIFY（要求验证）</li>
 *   <li>80-100：BLOCK（直接拒绝）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    /** 风控特征窗口（秒） */
    private static final int FEATURE_WINDOW_SECONDS = 60;
    /** 特征数据在 Redis 中的 TTL */
    private static final int FEATURE_TTL_SECONDS = 300;

    // ==================== 评分阈值常量 ====================
    private static final int SCORE_FREQ_HIGH = 20;
    private static final int SCORE_INHUMAN_SPEED = 40;
    private static final int SCORE_TOKEN_TOO_FAST = 25;
    private static final int SCORE_IP_MULTI_ACCOUNT = 30;
    private static final int SCORE_UA_SWITCHED = 20;
    private static final int SCORE_ACTIVITY_HOP = 15;
    private static final int SCORE_NEW_ACCOUNT = 10;
    private static final int SCORE_HIGH_SUCCESS_RATE = 15;

    // ==================== 阈值参数 ====================
    private static final int FREQ_THRESHOLD = 10;         // 每秒请求数阈值
    private static final long SPEED_THRESHOLD_MS = 80;    // 人类不可能达到的间隔
    private static final long TOKEN_SPEED_MS = 100;       // Token→执行太快
    private static final int IP_ACCOUNT_THRESHOLD = 3;    // 同 IP 账号数
    private static final int ACTIVITY_HOP_THRESHOLD = 3;   // 快速切换活动数
    private static final long NEW_ACCOUNT_DAYS = 7;       // 新账号天数
    private static final double HIGH_SUCCESS_THRESHOLD = 0.8; // 异常高的成功率
    /** BLOCK 决策累计达到此次数后自动加入黑名单 */
    private static final long BLOCK_BLACKLIST_THRESHOLD = 3;

    /**
     * 评估用户风险。
     *
     * @param userId     用户 ID
     * @param activityId 秒杀活动 ID
     * @param clientIp   客户端 IP
     * @param userAgent  客户端 User-Agent
     * @return 风控决策
     */
    public RiskDecision evaluateRisk(Long userId, Long activityId, String clientIp, String userAgent) {
        // 0. 黑名单前置检查：命中直接 BLOCK（满分），不再提取特征
        if (isBlacklisted(userId)) {
            RiskDecision decision = RiskDecision.builder()
                    .riskScore(100)
                    .riskLevel(RiskLevel.CRITICAL)
                    .action(RiskAction.BLOCK)
                    .reason("黑名单用户")
                    .build();
            // 黑名单命中的 BLOCK 照常发 MQ 审计事件；
            // 但不累加 BLOCK 计数——用户已在黑名单中，重复计数无意义
            sendDecisionEvent(decision, userId, activityId, clientIp, userAgent);
            log.warn("Blacklisted user blocked: userId={}, activityId={}", userId, activityId);
            return decision;
        }

        // 1. 提取行为特征
        RiskFeature feature = extractFeatures(userId, activityId, clientIp, userAgent);

        // 2. 计算风险评分
        int score = calculateScore(feature);

        // 3. 确定处置动作
        RiskDecision decision = decide(score, feature);

        // 4. 更新特征记录到 Redis（供下次评估使用）
        updateFeatureSnapshot(userId, activityId, clientIp, userAgent, score);

        // 5. 非放行决策发送 MQ 异步落库（DELAY/VERIFY/BLOCK）
        sendDecisionEvent(decision, userId, activityId, clientIp, userAgent);

        // 6. BLOCK 决策累积惩罚：计数 +1，达到阈值自动拉黑（24h）
        if (decision.getAction() == RiskAction.BLOCK) {
            recordBlockAndMaybeBlacklist(userId);
        }

        log.debug("Risk evaluation: userId={}, score={}, action={}, reason={}",
                userId, score, decision.getAction(), decision.getReason());

        return decision;
    }

    /**
     * 记录一次 BLOCK 决策：INCR 计数键（首次设置 24h TTL），
     * 24h 内累计达到 {@link #BLOCK_BLACKLIST_THRESHOLD} 次则自动加入黑名单。
     * 失败仅记录日志，不影响秒杀主流程。
     */
    private void recordBlockAndMaybeBlacklist(Long userId) {
        try {
            String countKey = RedisUtil.RISK_BLOCK_COUNT_PREFIX + userId;
            Long count = stringRedisTemplate.opsForValue().increment(countKey);
            if (count == null) {
                return;
            }
            if (count == 1L) {
                stringRedisTemplate.expire(countKey,
                        RedisUtil.RISK_BLACKLIST_TTL_HOURS, TimeUnit.HOURS);
            }
            if (count >= BLOCK_BLACKLIST_THRESHOLD) {
                addToBlacklist(userId);
                log.warn("User auto-blacklisted after {} BLOCK decisions in 24h: userId={}",
                        count, userId);
            }
        } catch (Exception e) {
            log.error("Failed to record block count: userId={}", userId, e);
        }
    }

    // ==================== 黑名单管理 ====================

    /** 手动拉黑：写入黑名单键（TTL 24h） */
    public void addToBlacklist(Long userId) {
        stringRedisTemplate.opsForValue().set(
                RedisUtil.RISK_BLACKLIST_PREFIX + userId, "1",
                RedisUtil.RISK_BLACKLIST_TTL_HOURS, TimeUnit.HOURS);
    }

    /** 解除拉黑：删除黑名单键与 BLOCK 计数键 */
    public void removeFromBlacklist(Long userId) {
        stringRedisTemplate.delete(RedisUtil.RISK_BLACKLIST_PREFIX + userId);
        stringRedisTemplate.delete(RedisUtil.RISK_BLOCK_COUNT_PREFIX + userId);
    }

    /** 判断用户是否在黑名单中 */
    public boolean isBlacklisted(Long userId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(RedisUtil.RISK_BLACKLIST_PREFIX + userId));
    }

    // ==================== 人机验证 ====================

    /** 判断用户是否已通过人机验证（risk:verified:{userId}:{activityId} 标记存在） */
    public boolean isVerified(Long userId, Long activityId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                RedisUtil.RISK_VERIFIED_PREFIX + userId + ":" + activityId));
    }

    /**
     * 发送风控决策事件到 MQ，由消费者异步写入 risk_decision_log 表。
     * <p>
     * 仅 DELAY/VERIFY/BLOCK 需要落库审计；发送失败仅记录日志，
     * 绝不影响秒杀主流程。
     * </p>
     */
    private void sendDecisionEvent(RiskDecision decision, Long userId, Long activityId,
                                   String clientIp, String userAgent) {
        if (decision.getAction() == RiskAction.PASS) {
            return;
        }
        try {
            RiskDecisionEvent event = RiskDecisionEvent.builder()
                    .userId(userId)
                    .activityId(activityId)
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .riskScore(decision.getRiskScore())
                    .riskLevel(decision.getRiskLevel().name())
                    .action(decision.getAction().name())
                    .reason(decision.getReason())
                    .timestamp(System.currentTimeMillis())
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.RISK_DECISION_EXCHANGE,
                    RabbitMQConfig.RISK_DECISION_ROUTING_KEY, event);
        } catch (Exception e) {
            log.error("Failed to send risk decision event: userId={}, activityId={}, action={}",
                    userId, activityId, decision.getAction(), e);
        }
    }

    /**
     * 从 Redis 提取用户行为特征。
     */
    private RiskFeature extractFeatures(Long userId, Long activityId, String clientIp, String userAgent) {
        RiskFeature.RiskFeatureBuilder builder = RiskFeature.builder();

        String userFeatureKey = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":" + activityId;
        String ipFeatureKey = RedisUtil.RISK_FEATURE_PREFIX + "ip:" + clientIp;

        // 频率特征：从 Redis ZSet（滑动窗口）中获取该用户对该活动的请求记录
        long now = System.currentTimeMillis();
        long windowStart = now - FEATURE_WINDOW_SECONDS * 1000;
        // 清理窗口外的过期记录
        stringRedisTemplate.opsForZSet().removeRangeByScore(userFeatureKey, 0, windowStart);
        Long count = stringRedisTemplate.opsForZSet().count(userFeatureKey, windowStart, now);
        builder.requestCount(count != null ? count.intValue() : 0);

        // 间隔特征：读取窗口内全部请求时间戳（member 即时间戳字符串），
        // 计算相邻请求间隔的最小值与平均值，识别脚本级高频请求
        Set<String> windowMembers = stringRedisTemplate.opsForZSet()
                .rangeByScore(userFeatureKey, windowStart, now);
        double[] intervalStats = calcIntervalStats(windowMembers);
        builder.minIntervalMs(intervalStats[0]);
        builder.avgIntervalMs(intervalStats[1]);

        // 速度特征：从 Redis Hash 读取上次 Token 时间
        String lastTokenTimeKey = userFeatureKey + ":token_time";
        String lastTokenTimeStr = stringRedisTemplate.opsForValue().get(lastTokenTimeKey);
        if (lastTokenTimeStr != null) {
            try {
                long lastTokenTime = Long.parseLong(lastTokenTimeStr);
                builder.tokenToExecuteMs(now - lastTokenTime);
            } catch (NumberFormatException ignored) {
                builder.tokenToExecuteMs(-1);
            }
        } else {
            builder.tokenToExecuteMs(-1);
        }

        // IP 特征：同 IP 下的活跃用户数
        Long ipUserCount = stringRedisTemplate.opsForSet().size(ipFeatureKey);
        builder.ipAccountCount(ipUserCount != null ? ipUserCount.intValue() : 0);

        // UA 特征：检查 UA 是否变化
        String lastUaKey = userFeatureKey + ":ua";
        String lastUa = stringRedisTemplate.opsForValue().get(lastUaKey);
        if (lastUa != null && userAgent != null && !lastUa.equals(userAgent)) {
            builder.uaSwitched(true);
        } else {
            builder.uaSwitched(false);
        }

        // 活动跳跃特征：用户最近访问过的活动数
        String activityHopKey = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":activities";
        Long activityCount = stringRedisTemplate.opsForZSet().size(activityHopKey);
        builder.activitySwitchCount(activityCount != null ? activityCount.intValue() : 0);

        // 历史特征
        String userHistoryKey = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":history";
        String registerDaysStr = (String) stringRedisTemplate.opsForHash().get(userHistoryKey, "accountAgeDays");
        if (registerDaysStr != null) {
            try {
                builder.accountAgeDays(Long.parseLong(registerDaysStr));
                builder.isNewAccount(Long.parseLong(registerDaysStr) < NEW_ACCOUNT_DAYS);
            } catch (NumberFormatException ignored) {
                builder.accountAgeDays(365).isNewAccount(false);
            }
        } else {
            builder.accountAgeDays(365).isNewAccount(false);
        }

        String successRateStr = (String) stringRedisTemplate.opsForHash().get(userHistoryKey, "successRate");
        if (successRateStr != null) {
            try {
                builder.historicalSuccessRate(Double.parseDouble(successRateStr));
            } catch (NumberFormatException ignored) {
                builder.historicalSuccessRate(0);
            }
        } else {
            builder.historicalSuccessRate(0);
        }

        return builder.build();
    }

    /**
     * 计算窗口内相邻请求间隔统计。
     * <p>
     * 将滑动窗口 member（请求时间戳字符串）解析为 long 并升序排序，
     * 对相邻差值取最小值和平均值。
     * </p>
     *
     * @param members 窗口内的 ZSet member 集合（时间戳字符串）
     * @return [最小间隔ms, 平均间隔ms]；不足 2 条有效记录时均为 -1
     */
    static double[] calcIntervalStats(Collection<String> members) {
        if (members == null || members.size() < 2) {
            return new double[]{-1, -1};
        }
        List<Long> timestamps = new ArrayList<>(members.size());
        for (String member : members) {
            try {
                timestamps.add(Long.parseLong(member));
            } catch (NumberFormatException ignored) {
                // 忽略窗口内的脏数据
            }
        }
        if (timestamps.size() < 2) {
            return new double[]{-1, -1};
        }
        Collections.sort(timestamps);
        long min = Long.MAX_VALUE;
        long sum = 0;
        for (int i = 1; i < timestamps.size(); i++) {
            long diff = timestamps.get(i) - timestamps.get(i - 1);
            min = Math.min(min, diff);
            sum += diff;
        }
        return new double[]{min, sum / (double) (timestamps.size() - 1)};
    }

    /**
     * 加权评分引擎。
     */
    private int calculateScore(RiskFeature f) {
        int score = 0;

        // 1. 请求频率过高
        double reqPerSec = f.getRequestCount() / (double) FEATURE_WINDOW_SECONDS;
        if (reqPerSec > FREQ_THRESHOLD) {
            score += SCORE_FREQ_HIGH;
        }

        // 2. 请求速度超出人类极限
        if (f.isInhumanSpeed()) {
            score += SCORE_INHUMAN_SPEED;
        }

        // 3. Token→执行间隔过短（无人为操作时间）
        if (f.getTokenToExecuteMs() > 0 && f.getTokenToExecuteMs() < TOKEN_SPEED_MS) {
            score += SCORE_TOKEN_TOO_FAST;
        }

        // 4. 同一 IP 多账号
        if (f.getIpAccountCount() >= IP_ACCOUNT_THRESHOLD) {
            score += SCORE_IP_MULTI_ACCOUNT;
        }

        // 5. UA 不一致
        if (f.isUaSwitched()) {
            score += SCORE_UA_SWITCHED;
        }

        // 6. 快速切换活动
        if (f.getActivitySwitchCount() >= ACTIVITY_HOP_THRESHOLD) {
            score += SCORE_ACTIVITY_HOP;
        }

        // 7. 新注册账号
        if (f.isNewAccount()) {
            score += SCORE_NEW_ACCOUNT;
        }

        // 8. 历史秒杀成功率异常高（可能是专业黄牛/脚本）
        if (f.getHistoricalSuccessRate() > HIGH_SUCCESS_THRESHOLD) {
            score += SCORE_HIGH_SUCCESS_RATE;
        }

        return Math.min(score, 100);
    }

    /**
     * 根据评分和特征做出决策。
     */
    private RiskDecision decide(int score, RiskFeature feature) {
        RiskDecision.RiskDecisionBuilder builder = RiskDecision.builder().riskScore(score);

        if (score < 30) {
            return builder
                    .riskLevel(RiskLevel.LOW)
                    .action(RiskAction.PASS)
                    .reason("正常用户行为")
                    .build();
        }

        if (score < 50) {
            return builder
                    .riskLevel(RiskLevel.MEDIUM)
                    .action(RiskAction.DELAY)
                    .reason("行为略有异常，已添加延迟")
                    .build();
        }

        if (score < 80) {
            StringBuilder reason = new StringBuilder("检测到多项异常：");
            if (feature.isInhumanSpeed()) reason.append("操作过快、");
            if (feature.getIpAccountCount() >= IP_ACCOUNT_THRESHOLD) reason.append("IP多账号、");
            if (feature.isUaSwitched()) reason.append("设备信息变化、");
            return builder
                    .riskLevel(RiskLevel.HIGH)
                    .action(RiskAction.VERIFY)
                    .reason(reason.substring(0, reason.length() - 1))
                    .build();
        }

        // >= 80
        StringBuilder reason = new StringBuilder("高度疑似脚本行为：");
        if (feature.isInhumanSpeed()) reason.append("请求速度超出人类极限、");
        if (feature.getTokenToExecuteMs() > 0 && feature.getTokenToExecuteMs() < TOKEN_SPEED_MS)
            reason.append("Token使用间隔过短、");
        if (feature.getIpAccountCount() >= IP_ACCOUNT_THRESHOLD) reason.append("IP下大量账号、");
        return builder
                .riskLevel(RiskLevel.CRITICAL)
                .action(RiskAction.BLOCK)
                .reason(reason.substring(0, Math.min(reason.length() - 1, 200)))
                .build();
    }

    /**
     * 更新 Redis 中的特征快照，记录本次请求数据。
     */
    private void updateFeatureSnapshot(Long userId, Long activityId, String clientIp,
                                        String userAgent, int score) {
        long now = System.currentTimeMillis();

        // 记录本次请求到滑动窗口
        String userFeatureKey = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":" + activityId;
        stringRedisTemplate.opsForZSet().add(userFeatureKey, String.valueOf(now), now);
        stringRedisTemplate.expire(userFeatureKey, FEATURE_TTL_SECONDS, TimeUnit.SECONDS);

        // 记录 UA
        if (userAgent != null) {
            stringRedisTemplate.opsForValue().set(userFeatureKey + ":ua", userAgent,
                    FEATURE_TTL_SECONDS, TimeUnit.SECONDS);
        }

        // 记录 IP 关联
        String ipFeatureKey = RedisUtil.RISK_FEATURE_PREFIX + "ip:" + clientIp;
        stringRedisTemplate.opsForSet().add(ipFeatureKey, String.valueOf(userId));
        stringRedisTemplate.expire(ipFeatureKey, FEATURE_TTL_SECONDS, TimeUnit.SECONDS);

        // 记录活动访问
        String activityHopKey = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":activities";
        stringRedisTemplate.opsForZSet().add(activityHopKey, String.valueOf(activityId), now);
        stringRedisTemplate.expire(activityHopKey, FEATURE_TTL_SECONDS, TimeUnit.SECONDS);

        // 记录最后一次评分
        stringRedisTemplate.opsForHash().put(
                RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":history",
                "lastScore", String.valueOf(score));
    }

    /**
     * 记录 Token 获取时间（Token 生成时调用）。
     * 用于后续评估 Token→执行间隔。
     */
    public void recordTokenGenerated(Long userId, Long activityId) {
        String key = RedisUtil.RISK_FEATURE_PREFIX + "user:" + userId + ":" + activityId + ":token_time";
        stringRedisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                FEATURE_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
