package com.smartseckill.common.aspect;

import com.google.common.util.concurrent.RateLimiter;
import com.smartseckill.common.annotation.RateLimit;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.user.security.LoginUser;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面。
 * <p>
 * 双层限流策略：
 * <ol>
 *   <li><b>本地限流（Guava RateLimiter）</b>：进程级令牌桶，零网络开销，兜底保护</li>
 *   <li><b>分布式限流（Redis Lua 滑动窗口）</b>：跨实例精确控制，共享全局限流配额</li>
 * </ol>
 * 先过本地再过分布式，本地不通过直接拒绝，减少 Redis 调用。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /** 本地 Guava 限流器缓存（按限流 Key 隔离） */
    private final Map<String, RateLimiter> localLimiters = new ConcurrentHashMap<>();

    /**
     * Redis 滑动窗口限流 Lua 脚本。
     * KEYS[1] = 限流 key
     * ARGV[1] = 窗口大小（秒）
     * ARGV[2] = 窗口内允许的最大请求数
     * 逻辑：移除窗口外的旧记录 → 统计当前窗口内的请求数 → 判断是否超限
     * 返回：1=允许, 0=限流
     */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1] " +
            "local windowSec = tonumber(ARGV[1]) " +
            "local maxPermits = tonumber(ARGV[2]) " +
            "local now = redis.call('TIME')[1] " +  // Redis 服务器时间（秒）
            "local windowStart = now - windowSec " +
            // 移除窗口外的记录
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart) " +
            // 统计当前窗口内的请求数
            "local count = redis.call('ZCARD', key) " +
            "if count >= maxPermits then " +
            "    return 0 " +
            "end " +
            // 记录本次请求（score=当前时间戳，member=now+随机数防冲突）
            "redis.call('ZADD', key, now, now .. ':' .. redis.call('INCR', key .. ':seq')) " +
            // 设置 Key 过期时间（窗口的 2 倍，防止僵尸 Key）
            "redis.call('EXPIRE', key, windowSec * 2) " +
            "return 1";

    @Around("@annotation(com.smartseckill.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String limitKey = buildLimitKey(rateLimit);
        double permitsPerSecond = rateLimit.permitsPerSecond();

        // 第一层：本地 Guava 限流（快速失败，无需网络 IO）
        RateLimiter localLimiter = localLimiters.computeIfAbsent(limitKey,
                k -> RateLimiter.create(permitsPerSecond));
        if (!localLimiter.tryAcquire(rateLimit.timeout(), rateLimit.timeUnit())) {
            log.warn("Local rate limit triggered: key={}", limitKey);
            throw new BusinessException(ResultCode.SECKILL_RATE_LIMIT.getCode(), rateLimit.message());
        }

        // 第二层：Redis 分布式滑动窗口限流（精确控制跨实例总量）
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SLIDING_WINDOW_LUA);
        script.setResultType(Long.class);

        String redisKey = RedisUtil.RATE_LIMIT_PREFIX + limitKey;
        // 滑动窗口 = 1 秒，窗口内最大请求数 = permitsPerSecond
        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(redisKey),
                String.valueOf(1), String.valueOf((int) permitsPerSecond));

        if (result == null || result == 0L) {
            log.warn("Distributed rate limit triggered: key={}", limitKey);
            throw new BusinessException(ResultCode.SECKILL_RATE_LIMIT.getCode(), rateLimit.message());
        }

        return joinPoint.proceed();
    }

    /** 根据注解配置构建限流 Key */
    private String buildLimitKey(RateLimit rateLimit) {
        String base = rateLimit.key();
        String dimension = rateLimit.dimension();

        switch (dimension) {
            case "user":
                Long userId = getCurrentUserId();
                return base + ":u:" + (userId != null ? userId : getClientIp());
            case "ip":
                return base + ":ip:" + getClientIp();
            case "global":
            default:
                return base + ":global";
        }
    }

    /** 获取当前登录用户 ID（从 SecurityContext 中提取） */
    private Long getCurrentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            if (principal instanceof LoginUser) {
                return ((LoginUser) principal).getUserId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 获取客户端 IP（从 HTTP 请求中提取） */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    return xff.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }
}
