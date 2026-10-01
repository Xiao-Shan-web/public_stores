package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.config.RabbitMQConfig;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 会员卡到期事件处理器（消息消费与定时扫描兜底共用）。
 * <p>
 * 消息体：Map { membershipId, userId, cardTypeName, kind }
 * kind：REMIND_7 / REMIND_1 / EXPIRE（缺省按 EXPIRE 处理，兼容历史消息）。
 * <p>
 * 全程幂等：未激活卡不处理、已停用/已过期跳过、提醒按标题去重 + 窗口校验、
 * 过期仅 ACTIVE→EXPIRED 成功时写消息。所有状态与时间均以查库结果为准，
 * 重复调用（消息重投 / 扫描与消息并发）安全。
 * <p>
 * <b>窗口校验</b>：长延迟消息被截断到 {@link MembershipExpirePolicy#MAX_SAFE_DELAY_MS}
 * 后会提前到达，此时提醒尚未进入提前 N 天窗口、到期消息尚未到 end_time，
 * 一律跳过，由 {@link MembershipExpireScanTask} 在窗口期兜底触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipExpireHandler {

    private static final String TITLE_REMIND_7 = "会员卡即将到期（7天）";
    private static final String TITLE_REMIND_1 = "会员卡即将到期（1天）";
    private static final String TITLE_EXPIRED = "会员卡已过期";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MembershipMapper membershipMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;

    /**
     * 处理一条会员卡到期相关事件（userId 取会员卡归属用户，忽略入参可传 null）。
     *
     * @param membershipId 会员卡ID
     * @param cardTypeName 卡类型名（可空，用于过期文案；空则按卡信息取名）
     * @param kind         事件类型（REMIND_7 / REMIND_1 / EXPIRE，非法值按 EXPIRE 处理）
     */
    public void handle(Long membershipId, String cardTypeName, String kind) {
        if (membershipId == null) {
            log.error("到期事件缺少 membershipId，丢弃：kind={}", kind);
            return;
        }

        Optional<Membership> opt = membershipMapper.findById(membershipId);
        if (opt.isEmpty()) {
            log.warn("会员卡{}不存在，跳过处理", membershipId);
            return;
        }

        Membership m = opt.get();
        String currentStatus = m.getStatus();

        switch (currentStatus) {
            case "DISABLED":
                log.info("会员卡{}已被管理员停用，kind={} 跳过", membershipId, kind);
                return;

            case "EXPIRED":
                log.info("会员卡{}已过期，kind={} 跳过（重复投递）", membershipId, kind);
                return;

            case "UNACTIVATED":
                // 消息只在激活后调度；未激活卡一律不处理，状态保持不变
                log.info("会员卡{}未激活，kind={} 跳过", membershipId, kind);
                return;

            case "ACTIVE":
                break;

            default:
                log.warn("会员卡{}状态未知：{}，跳过", membershipId, currentStatus);
                return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (RabbitMQConfig.KIND_REMIND_7.equals(kind) || RabbitMQConfig.KIND_REMIND_1.equals(kind)) {
            handleReminder(m, kind, now);
        } else {
            handleExpire(m, cardTypeName, now);
        }
    }

    /**
     * 到期前提醒：仅当已进入"到期前 N 天"窗口（end_time - N 天 &le; now &lt; end_time）
     * 且未发送过时写一条去重提醒消息。
     * <p>
     * 长延迟消息被截断提前到达时（now &lt; end_time - N 天）跳过，等扫描任务窗口期补发。
     */
    private void handleReminder(Membership m, String kind, LocalDateTime now) {
        if (m.getEndTime() == null || !m.getEndTime().isAfter(now)) {
            log.info("会员卡{}已到/过到期时间，跳过{}提醒", m.getId(), kind);
            return;
        }
        boolean sevenDay = RabbitMQConfig.KIND_REMIND_7.equals(kind);
        int days = sevenDay ? 7 : 1;
        String title = sevenDay ? TITLE_REMIND_7 : TITLE_REMIND_1;
        String cardName = displayName(m);

        // 窗口校验：未进入提前 N 天窗口说明消息被截断提前到达，跳过
        if (m.getEndTime().minusDays(days).isAfter(now)) {
            log.info("会员卡{}尚未进入{}提醒窗口（endTime={}），跳过（截断提前到达，待扫描兜底）",
                    m.getId(), kind, m.getEndTime());
            return;
        }

        Long userId = m.getUserId();
        if (userId != null && messageMapper.countReminder(userId, m.getId(), title) > 0) {
            log.info("会员卡{}的{}提醒已发送过，跳过（重复投递）", m.getId(), kind);
            return;
        }

        String content = "您的「" + cardName + "」将于 " + DATE_FMT.format(m.getEndTime())
                + " 到期（剩余约 " + days + " 天），请及时续费。";
        sendMessage(userId, m.getId(), title, content);
        log.info("会员卡{}已发送{}提醒", m.getId(), kind);
    }

    /**
     * 到期：仅当 ACTIVE 且 end_time&le;now 时标记 EXPIRED 并写消息（条件更新保证只执行一次）。
     * 截断提前到达（未到 end_time）时跳过，等扫描任务到期后补发。
     */
    private void handleExpire(Membership m, String cardTypeName, LocalDateTime now) {
        if (m.getEndTime() != null && m.getEndTime().isAfter(now)) {
            log.info("会员卡{}未到期（endTime={}），跳过过期处理（截断提前到达，待扫描兜底）",
                    m.getId(), m.getEndTime());
            return;
        }
        int rows = membershipMapper.markExpired(m.getId());
        if (rows <= 0) {
            log.info("会员卡{}状态已被其他流程更新，跳过过期消息", m.getId());
            return;
        }
        String cardName = cardTypeName != null ? cardTypeName : displayName(m);
        String content = "您的「" + cardName + "」已过期，请及时续费。";
        sendMessage(m.getUserId(), m.getId(), TITLE_EXPIRED, content);
        log.info("会员卡{}已标记 EXPIRED 并发送过期消息", m.getId());
    }

    /**
     * 写入一条 EXPIRE 系统消息并推送
     */
    private void sendMessage(Long userId, Long membershipId, String title, String content) {
        if (userId == null) {
            return;
        }
        Message msg = new Message();
        msg.setId(idGenerator.nextId());
        msg.setUserId(userId);
        msg.setType("EXPIRE");
        msg.setTitle(title);
        msg.setContent(content);
        msg.setRefId(membershipId);
        msg.setIsRead(0);
        messageMapper.insert(msg);
        notificationPushService.push(msg);

        log.info("已给用户{}发送会员卡消息：{}（membershipId={}）", userId, title, membershipId);
    }

    private String displayName(Membership m) {
        if (m.getCardTypeName() != null) return m.getCardTypeName();
        return m.getCardType() != null ? m.getCardType() : "会员卡";
    }
}
