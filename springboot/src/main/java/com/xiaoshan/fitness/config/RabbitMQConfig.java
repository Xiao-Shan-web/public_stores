package com.xiaoshan.fitness.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置
 * 1. 配置 JSON 序列化的 RabbitTemplate 与 Listener 工厂
 * 2. 声明会员卡到期延迟队列（依赖 x-delayed-message 插件）
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 序列化配置 ====================

    /**
     * RabbitTemplate 使用 JSON 序列化
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    /**
     * 消息监听容器使用 JSON 序列化
     * <p>
     * 确认模式采用 AUTO（Spring 自动管理 ack/nack）：
     * - 方法正常返回 → 自动 ack
     * - 方法抛异常 → 自动 nack，配合 defaultRequeueRejected=false 丢弃，避免死循环
     */
    @Bean
    public RabbitListenerContainerFactory<SimpleMessageListenerContainer> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory =
                new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    // ==================== 会员卡到期延迟队列 ====================

    public static final String MEMBERSHIP_EXPIRE_QUEUE = "membership.expire.queue";
    public static final String MEMBERSHIP_EXPIRE_EXCHANGE = "membership.expire.exchange";
    public static final String MEMBERSHIP_EXPIRE_ROUTING_KEY = "membership.expire";

    /** 延迟消息事件类型：到期前 7 天提醒 / 到期前 1 天提醒 / 到期 */
    public static final String KIND_REMIND_7 = "REMIND_7";
    public static final String KIND_REMIND_1 = "REMIND_1";
    public static final String KIND_EXPIRE = "EXPIRE";

    /**
     * 延迟队列（持久化）
     */
    @Bean
    public Queue membershipExpireQueue() {
        return QueueBuilder.durable(MEMBERSHIP_EXPIRE_QUEUE).build();
    }

    /**
     * 延迟交换机（x-delayed-message 插件，基于 direct 路由）
     */
    @Bean
    public CustomExchange membershipExpireExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(
                MEMBERSHIP_EXPIRE_EXCHANGE,
                "x-delayed-message",
                true,
                false,
                args
        );
    }

    /**
     * 绑定队列到延迟交换机
     */
    @Bean
    public Binding membershipExpireBinding() {
        return BindingBuilder.bind(membershipExpireQueue())
                .to(membershipExpireExchange())
                .with(MEMBERSHIP_EXPIRE_ROUTING_KEY)
                .and(null);
    }

}
