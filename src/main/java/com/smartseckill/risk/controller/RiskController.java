package com.smartseckill.risk.controller;

import com.smartseckill.common.result.Result;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 风控人机验证控制器。
 * <p>
 * 秒杀被判定为 VERIFY（要求验证）时，前端引导用户完成一道简单算术题：
 * <ol>
 *   <li>GET /risk/verify/challenge → 获取题目与一次性 token（答案缓存 5 分钟）</li>
 *   <li>POST /risk/verify {activityId, token, answer} → 校验答案，
 *       通过后写入 {@code risk:verified:{userId}:{activityId}} 标记（TTL 10 分钟），
 *       期间内秒杀请求命中 VERIFY 决策时直接放行</li>
 * </ol>
 * 复用项目"模拟验证码"思路，不引入第三方验证码服务。
 * </p>
 */
@Tag(name = "风控人机验证")
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    /** 验证题答案缓存过期时间（分钟） */
    private static final long CHALLENGE_TTL_MINUTES = 5;

    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "获取人机验证算术题")
    @GetMapping("/verify/challenge")
    public Result<Map<String, Object>> challenge() {
        int a = 1 + (int) (Math.random() * 9);
        int b = 1 + (int) (Math.random() * 9);
        String token = UUID.randomUUID().toString().replace("-", "");
        // 答案缓存 5 分钟，一次性使用
        stringRedisTemplate.opsForValue().set(
                RedisUtil.RISK_VERIFY_CHALLENGE_PREFIX + token,
                String.valueOf(a + b), CHALLENGE_TTL_MINUTES, TimeUnit.MINUTES);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("question", a + " + " + b + " = ?");
        return Result.success(data);
    }

    @Operation(summary = "提交人机验证答案，通过后 10 分钟内免验证")
    @PostMapping("/verify")
    public Result<Void> verify(@AuthenticationPrincipal LoginUser loginUser,
                               @RequestBody Map<String, Object> body) {
        Long activityId = Long.valueOf(String.valueOf(body.get("activityId")));
        String token = String.valueOf(body.get("token"));
        String answer = String.valueOf(body.get("answer"));

        String challengeKey = RedisUtil.RISK_VERIFY_CHALLENGE_PREFIX + token;
        String expected = stringRedisTemplate.opsForValue().get(challengeKey);
        if (expected == null) {
            return Result.fail(ResultCode.BAD_REQUEST, "验证题已过期，请重新获取");
        }
        // 一次性验证题：取出后立即作废，防止暴力重试
        stringRedisTemplate.delete(challengeKey);
        if (!expected.equals(answer)) {
            return Result.fail(ResultCode.BAD_REQUEST, "验证答案错误");
        }

        // 写入已验证标记：risk:verified:{userId}:{activityId}，TTL 10 分钟
        stringRedisTemplate.opsForValue().set(
                RedisUtil.RISK_VERIFIED_PREFIX + loginUser.getUserId() + ":" + activityId,
                "1", RedisUtil.RISK_VERIFIED_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.success();
    }
}
