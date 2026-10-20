package com.smartseckill.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列与交换机配置。
 * <p>
 * 定义三类消息通道：
 * <ul>
 *   <li>秒杀订单 — 异步创建秒杀订单，削峰填谷</li>
 *   <li>订单延迟取消 — TTL + 死信队列实现 30 分钟未支付自动取消</li>
 *   <li>风控日志 — 异步写入风控行为日志</li>
 * </ul>
 * </p>
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 秒杀订单队列 ====================
    /** 秒杀订单交换机 */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    /** 秒杀订单队列 */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    /** 秒杀订单路由键 */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";

    // ==================== 订单延迟取消队列 ====================
    /** 延迟交换机（消息在此等待 TTL 过期） */
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    /** 延迟队列 */
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    /** 延迟路由键 */
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.cancel";
    /** 死信交换机（TTL 过期后消息转发到此） */
    public static final String ORDER_DEAD_LETTER_EXCHANGE = "order.dead.letter.exchange";
    /** 死信队列（消费者监听此队列执行取消逻辑） */
    public static final String ORDER_DEAD_LETTER_QUEUE = "order.dead.letter.queue";
    /** 死信路由键 */
    public static final String ORDER_DEAD_LETTER_ROUTING_KEY = "order.dead.cancel";

    // ==================== 风控日志队列 ====================
    /** 风控日志交换机（Topic 类型，支持模式匹配） */
    public static final String RISK_LOG_EXCHANGE = "risk.log.exchange";
    /** 风控日志队列 */
    public static final String RISK_LOG_QUEUE = "risk.log.queue";
    /** 风控日志路由键 */
    public static final String RISK_LOG_ROUTING_KEY = "risk.log.write";

    // ==================== 秒杀订单 ====================

    /** 秒杀订单 Direct 交换机 */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return ExchangeBuilder.directExchange(SECKILL_ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 秒杀订单持久化队列 */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).build();
    }

    /** 绑定秒杀订单队列到交换机 */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    // ==================== 订单延迟取消（TTL + 死信队列） ====================

    /**
     * 延迟交换机。
     * TTL 在发送消息时逐条设置（非交换机级别），过期后由 RabbitMQ 自动转发到死信交换机。
     */
    @Bean
    public DirectExchange orderDelayExchange() {
        return ExchangeBuilder.directExchange(ORDER_DELAY_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 延迟队列，配置死信交换机和路由键，TTL 过期后消息自动转发 */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .deadLetterExchange(ORDER_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /** 绑定延迟队列到延迟交换机 */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    /** 死信交换机，接收 TTL 过期的消息 */
    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return ExchangeBuilder.directExchange(ORDER_DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 死信队列，{@link com.smartseckill.order.consumer.OrderTimeoutConsumer} 监听此队列 */
    @Bean
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DEAD_LETTER_QUEUE).build();
    }

    /** 绑定死信队列到死信交换机 */
    @Bean
    public Binding orderDeadLetterBinding() {
        return BindingBuilder.bind(orderDeadLetterQueue())
                .to(orderDeadLetterExchange())
                .with(ORDER_DEAD_LETTER_ROUTING_KEY);
    }

    // ==================== 风控日志 ====================

    /** 风控日志 Topic 交换机 */
    @Bean
    public TopicExchange riskLogExchange() {
        return ExchangeBuilder.topicExchange(RISK_LOG_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 风控日志持久化队列 */
    @Bean
    public Queue riskLogQueue() {
        return QueueBuilder.durable(RISK_LOG_QUEUE).build();
    }

    /** 绑定风控日志队列到交换机 */
    @Bean
    public Binding riskLogBinding() {
        return BindingBuilder.bind(riskLogQueue())
                .to(riskLogExchange())
                .with(RISK_LOG_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器，替代默认的 Java 序列化。
     * 消息体为 JSON 字符串，跨语言兼容，且体积更小。
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate，使用 JSON 序列化。
     * Spring Boot 自动配置的 RabbitTemplate 会拾取 Jackson2JsonMessageConverter Bean。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * RabbitAdmin：负责队列/交换机的自动声明，并提供队列深度查询能力
     * （{@code StockReconciliationJob} 库存恢复与 {@code CacheWarmUpRunner} 启动预热依赖）。
     * MQ 不可用时声明动作随连接重试，不影响上下文启动。
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
