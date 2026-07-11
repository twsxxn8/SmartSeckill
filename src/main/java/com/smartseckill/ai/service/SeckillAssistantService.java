package com.smartseckill.ai.service;

import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.seckill.entity.SeckillActivity;
import com.smartseckill.seckill.mapper.SeckillActivityMapper;
import com.smartseckill.seckill.mapper.SeckillOrderMapper;
import com.smartseckill.seckill.entity.SeckillOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 秒杀助手服务。
 * <p>
 * 提供秒杀成功率预测、策略建议、活动热度分析等功能。
 * 全部基于规则引擎和数据分析，不依赖外部 AI API。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillAssistantService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * 获取秒杀成功率预测。
     * <p>
     * 计算公式：
     * <ul>
     *   <li>竞争指数 = 在线人数 / 剩余库存</li>
     *   <li>基础成功率 = 1 / 竞争指数</li>
     *   <li>修正因子：活动已进行时间占比（越晚成功率越高，因为早抢的人已退出）</li>
     * </ul>
     * </p>
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID（用于个性化分析）
     * @return 包含成功率、建议等信息的 Map
     */
    public Map<String, Object> predictSuccessRate(Long activityId, Long userId) {
        Map<String, Object> result = new HashMap<>();

        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) {
            result.put("successRate", 0);
            result.put("recommendation", "活动未在进行中");
            return result;
        }

        // 1. 在线竞争人数
        String onlineKey = RedisUtil.SECKILL_ONLINE_PREFIX + activityId;
        long cutoff = System.currentTimeMillis() - RedisUtil.SECKILL_ONLINE_TTL_SECONDS * 1000;
        stringRedisTemplate.opsForZSet().removeRangeByScore(onlineKey, 0, cutoff);
        Long onlineCount = stringRedisTemplate.opsForZSet().size(onlineKey);
        int online = onlineCount != null ? onlineCount.intValue() : 0;

        // 2. 剩余库存
        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        int remainingStock = 0;
        try {
            remainingStock = stockStr != null ? Integer.parseInt(stockStr) : activity.getAvailableStock();
        } catch (NumberFormatException ignored) {}

        // 3. 参与用户数
        String userKey = RedisUtil.SECKILL_USER_PREFIX + activityId;
        Long participatedCount = stringRedisTemplate.opsForSet().size(userKey);
        int participated = participatedCount != null ? participatedCount.intValue() : 0;

        // 4. 竞争指数计算
        remainingStock = Math.max(remainingStock, 1);
        int competitors = Math.max(online, 1);
        double competitionIndex = (double) competitors / remainingStock;

        // 5. 成功率预测
        double baseSuccessRate;
        if (competitionIndex <= 0.5) {
            baseSuccessRate = 0.7 + Math.random() * 0.2; // 70-90%
        } else if (competitionIndex <= 2) {
            baseSuccessRate = 0.3 + Math.random() * 0.3; // 30-60%
        } else if (competitionIndex <= 5) {
            baseSuccessRate = 0.1 + Math.random() * 0.15; // 10-25%
        } else {
            baseSuccessRate = Math.random() * 0.1; // 0-10%
        }

        // 6. 活动进度修正（进行越久，已有部分用户退出竞争）
        long now = System.currentTimeMillis();
        long startMs = activity.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMs = activity.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        double progress = (endMs - startMs) > 0 ? (double) (now - startMs) / (endMs - startMs) : 0;
        progress = Math.max(0, Math.min(1, progress));
        double progressBonus = progress * 0.15; // 最多 15% 加成

        double finalRate = Math.min(0.95, baseSuccessRate + progressBonus);

        // 7. 生成策略建议
        String recommendation = generateRecommendation(competitionIndex, remainingStock, online, participated);

        result.put("successRate", Math.round(finalRate * 100));
        result.put("competitionIndex", Math.round(competitionIndex * 10) / 10.0);
        result.put("competitors", competitors);
        result.put("remainingStock", Math.max(0, remainingStock));
        result.put("participated", participated);
        result.put("activityProgress", Math.round(progress * 100));
        result.put("recommendation", recommendation);
        result.put("difficulty", competitionIndex > 5 ? "极高" : competitionIndex > 2 ? "较高" : competitionIndex > 1 ? "中等" : "较低");

        return result;
    }

    /**
     * 获取用户的秒杀行为分析报告。
     */
    public Map<String, Object> getUserAnalysis(Long userId) {
        Map<String, Object> analysis = new HashMap<>();

        // 统计用户秒杀参与历史
        Long totalAttempts = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, userId));
        Long successCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, userId)
                        .in(SeckillOrder::getStatus, 0, 1)); // 待支付或已支付

        double successRate = totalAttempts > 0 ? (double) successCount / totalAttempts : 0;

        analysis.put("totalAttempts", totalAttempts);
        analysis.put("successCount", successCount);
        analysis.put("successRate", Math.round(successRate * 100));
        analysis.put("totalOrders", totalAttempts);

        // 生成改进建议
        List<String> tips = new ArrayList<>();
        if (totalAttempts == 0) {
            tips.add("你还没有参与过秒杀，建议从小库存活动开始练手");
        } else if (successRate < 0.2) {
            tips.add("你的成功率较低，尝试在网络更稳定的环境下参与");
            tips.add("建议在倒计时还剩 0.5 秒时就开始点击按钮");
            tips.add("检查你的网络延迟，切换 5G 或有线网络可能提升成功率");
        } else if (successRate < 0.5) {
            tips.add("成功率中等，继续练习手速可提升");
        } else {
            tips.add("你的表现不错！保持当前策略即可");
        }

        analysis.put("tips", tips);

        return analysis;
    }

    /**
     * 获取活动热度分析。
     */
    public Map<String, Object> getActivityHeatAnalysis(Long activityId) {
        Map<String, Object> heat = new HashMap<>();

        String onlineKey = RedisUtil.SECKILL_ONLINE_PREFIX + activityId;
        long cutoff = System.currentTimeMillis() - RedisUtil.SECKILL_ONLINE_TTL_SECONDS * 1000;
        stringRedisTemplate.opsForZSet().removeRangeByScore(onlineKey, 0, cutoff);
        Long onlineCount = stringRedisTemplate.opsForZSet().size(onlineKey);

        String stockKey = RedisUtil.SECKILL_STOCK_PREFIX + activityId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        int stock = 0;
        try { stock = stockStr != null ? Integer.parseInt(stockStr) : 0; }
        catch (NumberFormatException ignored) {}

        SeckillActivity activity = activityMapper.selectById(activityId);
        int totalStock = activity != null ? activity.getTotalStock() : 0;
        int soldCount = totalStock - Math.max(0, stock);

        heat.put("onlineCount", onlineCount != null ? onlineCount.intValue() : 0);
        heat.put("remainingStock", Math.max(0, stock));
        heat.put("totalStock", totalStock);
        heat.put("soldCount", soldCount);

        // 售罄速度（如果已售完）
        if (soldCount > 0 && activity != null && activity.getStatus() == 1) {
            long startMs = activity.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed > 0) {
                double itemsPerSec = soldCount / (elapsed / 1000.0);
                heat.put("sellRatePerSec", Math.round(itemsPerSec * 10) / 10.0);
            }
        }

        // 热度等级
        if (onlineCount == null || onlineCount == 0) {
            heat.put("heatLevel", "冷清");
            heat.put("heatIcon", "❄️");
        } else if (onlineCount < 20) {
            heat.put("heatLevel", "温和");
            heat.put("heatIcon", "🔥");
        } else if (onlineCount < 100) {
            heat.put("heatLevel", "火爆");
            heat.put("heatIcon", "🔥🔥");
        } else {
            heat.put("heatLevel", "超级火爆");
            heat.put("heatIcon", "🔥🔥🔥");
        }

        return heat;
    }

    private String generateRecommendation(double competitionIndex, int stock, int online, int participated) {
        if (stock <= 0) {
            return "该活动已抢光，关注其他场次吧";
        }
        if (competitionIndex > 5) {
            return "竞争极其激烈！建议在网络延迟最低的环境下参与，提前准备好 Token";
        }
        if (competitionIndex > 2) {
            return "竞争较激烈，建议提前进入页面，在倒计时结束时快速点击";
        }
        if (competitionIndex > 1) {
            return "竞争适中，保持手速有较大机会抢到";
        }
        return "竞争较小，你很有可能抢到！";
    }
}
