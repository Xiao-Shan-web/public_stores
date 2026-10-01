package com.xiaoshan.fitness.consumer;

import com.xiaoshan.fitness.config.RabbitMQConfig;
import com.xiaoshan.fitness.service.MembershipExpireHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 消息消费者
 * 监听会员卡到期延迟队列，解析消息后委托 {@link MembershipExpireHandler} 处理。
 * <p>
 * 消息体：Map { membershipId, userId, cardTypeName, kind }
 * kind：REMIND_7 / REMIND_1 / EXPIRE（缺省按 EXPIRE 处理，兼容历史消息）。
 * <p>
 * 处理全程幂等（详见 Handler）。注意：长延迟消息被生产端截断到插件安全上限后
 * 会提前到达，Handler 的窗口校验会跳过，由 MembershipExpireScanTask 定时兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQConsumer {

    private final MembershipExpireHandler membershipExpireHandler;

    @RabbitListener(queues = RabbitMQConfig.MEMBERSHIP_EXPIRE_QUEUE)
    public void handleMembershipExpire(Map<String, Object> payload) {
        Long membershipId = toLong(payload.get("membershipId"));
        Long userId = toLong(payload.get("userId"));
        String cardTypeName = (String) payload.get("cardTypeName");
        String kind = payload.get("kind") == null
                ? RabbitMQConfig.KIND_EXPIRE : payload.get("kind").toString();

        log.info("收到会员卡到期消息，membershipId={}, userId={}, kind={}, cardTypeName={}",
                membershipId, userId, kind, cardTypeName);

        membershipExpireHandler.handle(membershipId, cardTypeName, kind);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
