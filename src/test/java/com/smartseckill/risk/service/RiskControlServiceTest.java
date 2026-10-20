package com.smartseckill.risk.service;

import com.smartseckill.risk.model.RiskDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskControlService 单元测试（Mockito 纯打桩，不依赖真实 Redis / MQ）。
 * <p>
 * 覆盖两个断链修复场景：
 * <ul>
 *   <li>请求间隔特征采集（minIntervalMs/avgIntervalMs）→ +40 脚本速度评分</li>
 *   <li>Token 生成时间记录（token_time）→ +25 Token 过快评分</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskControlService 风控评分单元测试")
class RiskControlServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private RiskControlService riskControlService;

    private static final Long USER_ID = 1001L;
    private static final Long ACTIVITY_ID = 2001L;
    private static final String CLIENT_IP = "10.0.0.1";
    private static final String USER_AGENT = "JUnit-Agent";

    /** evaluateRisk 会用到四类 Redis 操作，统一打桩；具体返回值由各用例自行覆盖 */
    private void stubRedisOps() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
    }

    // ==================== 黑名单前置检查 ====================

    @Test
    @DisplayName("黑名单用户 → 直接 BLOCK 100 分，不提取特征、不累加计数")
    void blacklistedUserShouldBlockImmediately() {
        when(stringRedisTemplate.hasKey("risk:blacklist:" + USER_ID)).thenReturn(true);

        RiskDecision decision = riskControlService.evaluateRisk(USER_ID, ACTIVITY_ID, CLIENT_IP, USER_AGENT);

        assertThat(decision.getRiskScore()).isEqualTo(100);
        assertThat(decision.getRiskLevel()).isEqualTo(RiskDecision.RiskLevel.CRITICAL);
        assertThat(decision.getAction()).isEqualTo(RiskDecision.RiskAction.BLOCK);
        assertThat(decision.getReason()).isEqualTo("黑名单用户");
        // 不再提取特征（无 ZSet 操作），也不累加 BLOCK 计数
        verify(stringRedisTemplate, never()).opsForZSet();
        verify(stringRedisTemplate, never()).opsForValue();
        // 黑名单命中的 BLOCK 仍发送 MQ 审计事件
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("BLOCK 决策 → block 计数 +1，24h 内达 3 次自动写入黑名单")
    void blockDecisionShouldIncrementCountAndAutoBlacklist() {
        stubRedisOps();
        long now = System.currentTimeMillis();
        // 构造 90 分 BLOCK 特征：间隔 50ms(+40) + 同 IP 3 账号(+30) + UA 变化(+20)
        Set<String> members = new HashSet<>(Arrays.asList(
                String.valueOf(now - 50), String.valueOf(now)));
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);
        when(setOps.size(anyString())).thenReturn(3L);
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return key.endsWith(":ua") ? "Old-Agent" : null;
        });
        // 本次为第 3 次 BLOCK → 触发自动拉黑
        when(valueOps.increment("risk:block:count:" + USER_ID)).thenReturn(3L);

        RiskDecision decision = riskControlService.evaluateRisk(USER_ID, ACTIVITY_ID, CLIENT_IP, USER_AGENT);

        assertThat(decision.getAction()).isEqualTo(RiskDecision.RiskAction.BLOCK);
        verify(valueOps).increment("risk:block:count:" + USER_ID);
        // 达到阈值 → 写入黑名单键（TTL 24h）
        verify(valueOps).set(eq("risk:blacklist:" + USER_ID), eq("1"),
                anyLong(), any(TimeUnit.class));
    }

    // ==================== 修复点1：请求间隔特征 → +40 ====================

    @Test
    @DisplayName("窗口内两条间隔50ms的请求 → isInhumanSpeed 生效 → 评分 +40 → DELAY")
    void inhumanSpeedShouldAdd40Score() {
        stubRedisOps();
        long now = System.currentTimeMillis();
        Set<String> members = new HashSet<>(Arrays.asList(
                String.valueOf(now - 50), String.valueOf(now)));
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);

        RiskDecision decision = riskControlService.evaluateRisk(USER_ID, ACTIVITY_ID, CLIENT_IP, USER_AGENT);

        // 其他特征均未命中，评分应恰好为 SCORE_INHUMAN_SPEED(40)
        assertThat(decision.getRiskScore()).isEqualTo(40);
        assertThat(decision.getAction()).isEqualTo(RiskDecision.RiskAction.DELAY);
    }

    @Test
    @DisplayName("窗口内仅1条记录 → minIntervalMs=-1 → 不触发 +40 → PASS")
    void singleRecordShouldNotTriggerInhumanSpeed() {
        stubRedisOps();
        Set<String> members = Collections.singleton(String.valueOf(System.currentTimeMillis()));
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(members);

        RiskDecision decision = riskControlService.evaluateRisk(USER_ID, ACTIVITY_ID, CLIENT_IP, USER_AGENT);

        assertThat(decision.getRiskScore()).isEqualTo(0);
        assertThat(decision.getAction()).isEqualTo(RiskDecision.RiskAction.PASS);
    }

    // ==================== 修复点2：Token→执行间隔 → +25 ====================

    @Test
    @DisplayName("Token 生成后 40ms 即执行 → 评分 +25")
    void fastTokenUseShouldAdd25Score() {
        stubRedisOps();
        // valueOps.get 会被 token_time 与 ua 两个 key 调用：token_time 返回 40ms 前的时间戳，其余返回 null
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return key.endsWith(":token_time")
                    ? String.valueOf(System.currentTimeMillis() - 40)
                    : null;
        });

        RiskDecision decision = riskControlService.evaluateRisk(USER_ID, ACTIVITY_ID, CLIENT_IP, USER_AGENT);

        // 其他特征均未命中，评分应恰好为 SCORE_TOKEN_TOO_FAST(25)
        assertThat(decision.getRiskScore()).isEqualTo(25);
        assertThat(decision.getAction()).isEqualTo(RiskDecision.RiskAction.PASS);
    }

    // ==================== 间隔统计算法直接覆盖 ====================

    @Test
    @DisplayName("calcIntervalStats：两条间隔50ms的记录 → min=avg=50")
    void calcIntervalStatsWithTwoRecords() {
        double[] stats = RiskControlService.calcIntervalStats(Arrays.asList("1000", "1050"));
        assertThat(stats).containsExactly(50.0, 50.0);
    }

    @Test
    @DisplayName("calcIntervalStats：乱序三条记录 → 排序后正确计算 min/avg")
    void calcIntervalStatsUnordered() {
        // 排序后 1000,1060,1100 → 间隔 60,40 → min=40, avg=50
        double[] stats = RiskControlService.calcIntervalStats(
                Arrays.asList("1100", "1000", "1060"));
        assertThat(stats).containsExactly(40.0, 50.0);
    }

    @Test
    @DisplayName("calcIntervalStats：null / 单条 / 脏数据 → [-1, -1]")
    void calcIntervalStatsInsufficientRecords() {
        assertThat(RiskControlService.calcIntervalStats(null)).containsExactly(-1.0, -1.0);
        assertThat(RiskControlService.calcIntervalStats(
                Collections.singletonList("1000"))).containsExactly(-1.0, -1.0);
        assertThat(RiskControlService.calcIntervalStats(
                Arrays.asList("not-a-number", "1000"))).containsExactly(-1.0, -1.0);
    }
}
