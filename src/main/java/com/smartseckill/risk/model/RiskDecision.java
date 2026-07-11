package com.smartseckill.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控决策结果。
 * <p>
 * 由 {@link com.smartseckill.risk.service.RiskControlService} 生成，
 * 包含风险评分、风险等级和处置动作。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecision {

    /** 风险评分（0-100），越高越可疑 */
    private int riskScore;

    /** 风险等级 */
    private RiskLevel riskLevel;

    /** 处置动作 */
    private RiskAction action;

    /** 决策原因（可读文本，用于日志和前端提示） */
    private String reason;

    /** 风险等级枚举 */
    public enum RiskLevel {
        /** 低风险：正常用户，评分 0-29 */
        LOW,
        /** 中风险：轻度可疑，评分 30-59 */
        MEDIUM,
        /** 高风险：明显异常，评分 60-79 */
        HIGH,
        /** 极高风险：基本确定是脚本/攻击，评分 80-100 */
        CRITICAL
    }

    /** 风控处置动作枚举 */
    public enum RiskAction {
        /** 放行：请求正常处理 */
        PASS,
        /** 延迟：添加 100-500ms 随机延迟后放行（温和限流） */
        DELAY,
        /** 验证：要求用户完成验证（滑块/验证码） */
        VERIFY,
        /** 拦截：直接拒绝请求 */
        BLOCK
    }
}
