package com.smartseckill.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀防重 Token 工具。
 * <p>
 * 解决高并发下同一用户重复提交秒杀请求的问题：
 * <ol>
 *   <li>秒杀开始前，前端调用接口获取 token（存入 Redis，有过期时间）</li>
 *   <li>秒杀请求必须携带 token，后端原子校验并删除 token</li>
 *   <li>token 校验失败 = 重复提交或非法请求，直接拒绝</li>
 * </ol>
 * Token 生命周期：获取后 5 分钟内有效，使用后立即销毁。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillTokenUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public static final String SECKILL_TOKEN_PREFIX = "seckill:token:";
    private static final long TOKEN_TTL_MINUTES = 5;

    /**
     * 原子校验并消费 Token 的 Lua 脚本。
     * KEYS[1] = token key
     * ARGV[1] = 期望的 token 值
     * 逻辑：GET 校验 → 若匹配则 DEL → 返回 1；不匹配则返回 0
     * 返回：1=校验通过并已删除, 0=Token 无效或已被使用
     */
    private static final String CHECK_AND_DELETE_LUA =
            "local key = KEYS[1] " +
            "local expectedToken = ARGV[1] " +
            "local actualToken = redis.call('GET', key) " +
            "if actualToken == expectedToken then " +
            "    redis.call('DEL', key) " +
            "    return 1 " +
            "end " +
            "return 0";

    /**
     * 为指定用户+活动生成防重 Token。
     *
     * @param userId     用户 ID
     * @param activityId 秒杀活动 ID
     * @return 生成的 Token（客户端需携带此 Token 发起秒杀请求）
     */
    public String generateToken(Long userId, Long activityId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = SECKILL_TOKEN_PREFIX + userId + ":" + activityId;
        stringRedisTemplate.opsForValue().set(key, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Seckill token generated: userId={}, activityId={}", userId, activityId);
        return token;
    }

    /**
     * 原子校验并消费 Token（一次性使用）。
     * 同一 Token 只能通过一次，防止重复秒杀。
     *
     * @param userId     用户 ID
     * @param activityId 秒杀活动 ID
     * @param token      客户端提交的 Token
     * @return true=校验通过, false=Token 无效或已被使用
     */
    public boolean validateAndConsumeToken(Long userId, Long activityId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String key = SECKILL_TOKEN_PREFIX + userId + ":" + activityId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(CHECK_AND_DELETE_LUA);
        script.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(key), token);

        if (result == null || result == 0L) {
            log.warn("Seckill token validation failed: userId={}, activityId={}", userId, activityId);
            return false;
        }
        return true;
    }
}
