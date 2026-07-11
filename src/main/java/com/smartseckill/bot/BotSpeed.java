package com.smartseckill.bot;

/**
 * 机器人反应速度类型。
 * <p>
 * FAST: 30-100ms Token to execute interval, simulator of human expert
 * NORMAL: 100-300ms, typical human speed
 * SLOW: 300-800ms, slower human, unlikely to succeed in high-competition scenarios
 * </p>
 */
public enum BotSpeed {
    /** Fast reaction: 30-100ms, ~15% of bots */
    FAST,
    /** Normal reaction: 100-300ms, ~60% of bots */
    NORMAL,
    /** Slow reaction: 300-800ms, ~25% of bots (usually fail to grab) */
    SLOW
}
