package com.smartseckill.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控决策日志，对应表 {@code risk_decision_log}。
 * <p>记录被延迟/验证/拦截的风控决策，供审计追溯与规则调优。</p>
 */
@Data
@TableName("risk_decision_log")
public class RiskDecisionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long activityId;
    private String clientIp;
    private String userAgent;
    private Integer riskScore;
    private String riskLevel;
    private String action;
    private String reason;
    private LocalDateTime createdAt;
}
