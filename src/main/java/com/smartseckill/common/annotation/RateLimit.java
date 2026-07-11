package com.smartseckill.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流注解。
 * <p>
 * 标注在 Controller 方法上，通过 AOP 实现分布式（Redis Lua 滑动窗口）+ 本地（Guava）双重限流。
 * 可用于秒杀、登录、验证码等高并发接口。
 * </p>
 *
 * <pre>{@code
 *   @RateLimit(key = "seckill:execute", permitsPerSecond = 500, timeout = 1, timeUnit = TimeUnit.SECONDS)
 *   public Result<Void> executeSeckill(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流 Key 前缀（完整 Key = prefix + ":" + 用户/ IP 维度） */
    String key();

    /** 每秒允许的请求数（令牌桶速率） */
    double permitsPerSecond() default 100;

    /** 获取令牌的超时时间 */
    long timeout() default 1;

    /** 超时时间单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 限流维度：ip / user / global */
    String dimension() default "user";

    /** 限流触发时返回的提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
