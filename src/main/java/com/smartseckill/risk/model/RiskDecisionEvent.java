package com.smartseckill.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 风控决策事件消息。
 * <p>
 * {@link com.smartseckill.risk.service.RiskControlService} 对 DELAY/VERIFY/BLOCK
 * 决策发送此消息到 RabbitMQ，由
 * {@link com.smartseckill.risk.consumer.RiskDecisionConsumer} 消费并异步落库。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecisionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;
    /** 秒杀活动 ID */
    private Long activityId;
    /** 客户端 IP */
    private String clientIp;
    /** 客户端 User-Agent */
    private String userAgent;
    /** 风险评分（0-100） */
    private Integer riskScore;
    /** 风险等级（LOW/MEDIUM/HIGH/CRITICAL） */
    private String riskLevel;
    /** 处置动作（DELAY/VERIFY/BLOCK） */
    private String action;
    /** 决策原因 */
    private String reason;
    /** 决策时间戳（毫秒） */
    private Long timestamp;
}
