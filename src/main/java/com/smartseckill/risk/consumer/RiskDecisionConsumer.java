package com.smartseckill.risk.consumer;

import com.smartseckill.common.config.RabbitMQConfig;
import com.smartseckill.risk.entity.RiskDecisionLog;
import com.smartseckill.risk.mapper.RiskDecisionLogMapper;
import com.smartseckill.risk.model.RiskDecisionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 风控决策日志异步消费者。
 * <p>
 * 消费 {@link com.smartseckill.risk.service.RiskControlService} 发出的
 * DELAY/VERIFY/BLOCK 决策事件，写入 {@code risk_decision_log} 表，
 * 将落库 IO 从秒杀热路径剥离。
 * </p>
 * <p>
 * 消费失败仅记录日志、不再重试：决策日志属于审计数据，
 * 单条丢失不影响业务正确性，避免脏消息阻塞队列。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskDecisionConsumer {

    private final RiskDecisionLogMapper riskDecisionLogMapper;

    /**
     * 消费风控决策事件并落库。
     * created_at 由数据库默认值填充（事件时间与落库时间差在毫秒级）。
     */
    @RabbitListener(queues = RabbitMQConfig.RISK_DECISION_QUEUE)
    public void handleRiskDecision(RiskDecisionEvent event) {
        try {
            RiskDecisionLog decisionLog = new RiskDecisionLog();
            decisionLog.setUserId(event.getUserId());
            decisionLog.setActivityId(event.getActivityId());
            decisionLog.setClientIp(event.getClientIp());
            decisionLog.setUserAgent(event.getUserAgent());
            decisionLog.setRiskScore(event.getRiskScore());
            decisionLog.setRiskLevel(event.getRiskLevel());
            decisionLog.setAction(event.getAction());
            decisionLog.setReason(event.getReason());
            riskDecisionLogMapper.insert(decisionLog);
            log.debug("Risk decision logged: userId={}, activityId={}, action={}, score={}",
                    event.getUserId(), event.getActivityId(), event.getAction(), event.getRiskScore());
        } catch (Exception e) {
            log.error("Failed to persist risk decision log: userId={}, activityId={}, action={}",
                    event.getUserId(), event.getActivityId(), event.getAction(), e);
        }
    }
}
