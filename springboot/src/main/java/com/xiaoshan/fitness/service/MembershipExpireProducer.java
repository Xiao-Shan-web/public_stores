package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 会员卡到期相关延迟消息生产者。
 * <p>
 * 通过 x-delayed-message 插件的 x-delay header 控制投递延迟。
 * 事件类型 {@code kind}：
 * <ul>
 *   <li>{@link RabbitMQConfig#KIND_REMIND_7}：到期前 7 天提醒；</li>
 *   <li>{@link RabbitMQConfig#KIND_REMIND_1}：到期前 1 天提醒；</li>
 *   <li>{@link RabbitMQConfig#KIND_EXPIRE}：到期时刻标记过期。</li>
 * </ul>
 * 唯一触发点：会员卡<b>激活成功后</b>（{@link MembershipActivationService}）；购买支付不发消息。
 * <p>
 * 延迟超过 {@link MembershipExpirePolicy#MAX_SAFE_DELAY_MS}（插件 32 位安全上限）时自动截断，
 * 截断后提前到达的消息会被消费端窗口校验跳过，由定时扫描任务（MembershipExpireScanTask）兜底触发。
 * 消费者对重复投递幂等，多发无害。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipExpireProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MembershipExpirePolicy membershipExpirePolicy;

    /**
     * 发送一条会员卡到期相关延迟消息。
     *
     * @param membershipId 会员卡ID
     * @param userId       用户ID
     * @param cardTypeName 卡类型名（通知文案用）
     * @param kind         事件类型（REMIND_7 / REMIND_1 / EXPIRE，非法值按 EXPIRE 处理）
     * @param delayMs      期望延迟毫秒数（超过安全上限自动截断到上限）
     */
    public void schedule(Long membershipId, Long userId, String cardTypeName, String kind, long delayMs) {
        String eventKind = normalizeKind(kind);
        long actualDelay = membershipExpirePolicy.cappedDelayMs(delayMs);
        boolean truncated = actualDelay < delayMs;

        Map<String, Object> payload = new HashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("userId", userId);
        payload.put("cardTypeName", cardTypeName);
        payload.put("kind", eventKind);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MEMBERSHIP_EXPIRE_EXCHANGE,
                RabbitMQConfig.MEMBERSHIP_EXPIRE_ROUTING_KEY,
                payload,
                message -> {
                    message.getMessageProperties().getHeaders().put("x-delay", actualDelay);
                    return message;
                }
        );

        if (truncated) {
            log.info("已发送会员卡{}延迟消息：kind={}，userId={}，卡类型={}，原始延迟={}ms 超出插件安全上限，已截断为={}ms（剩余由定时扫描兜底）",
                    membershipId, eventKind, userId, cardTypeName, delayMs, actualDelay);
        } else {
            log.info("已发送会员卡{}延迟消息：kind={}，userId={}，卡类型={}，延迟={}ms",
                    membershipId, eventKind, userId, cardTypeName, actualDelay);
        }
    }

    private String normalizeKind(String kind) {
        if (RabbitMQConfig.KIND_REMIND_7.equals(kind)
                || RabbitMQConfig.KIND_REMIND_1.equals(kind)
                || RabbitMQConfig.KIND_EXPIRE.equals(kind)) {
            return kind;
        }
        return RabbitMQConfig.KIND_EXPIRE;
    }
}
