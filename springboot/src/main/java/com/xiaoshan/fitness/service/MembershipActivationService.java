package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.config.RabbitMQConfig;
import com.xiaoshan.fitness.entity.CardType;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.mapper.CardTypeMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会员卡激活服务（用户端「立即激活」与管理员「手动激活」共用）
 * <p>
 * 激活规则：
 * <ul>
 *   <li>仅 UNACTIVATED 状态的卡可激活；</li>
 *   <li>互斥：同一用户、同一卡种分类（NORMAL/PT）、适用范围有交集时仅允许一张 ACTIVE：
 *       全店通用卡与任何同类卡冲突；两张单店卡仅在同一门店冲突；不同门店互不影响；
 *       普通卡与私教课卡互不冲突，可同时生效；</li>
 *   <li>start_time=now，end_time=now+duration_days 天（普通卡与按节私教卡同口径）；</li>
 *   <li>激活成功后才按 end_time 动态调度到期前 7 天/1 天提醒与到期延迟消息：
 *       剩余有效期不足对应提前量则跳过提醒，到期消息始终发送。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MembershipActivationService {

    private static final String CATEGORY_NORMAL = "NORMAL";
    private static final String CATEGORY_PT = "PT";
    private static final String SCOPE_ALL = "ALL_STORE";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MembershipMapper membershipMapper;
    private final CardTypeMapper cardTypeMapper;
    private final MembershipExpirePolicy membershipExpirePolicy;
    private final MembershipExpireProducer membershipExpireProducer;
    private final SmsService smsService;

    /**
     * 激活指定会员卡
     *
     * @param membershipId 会员卡ID
     * @param userId       用户ID（用于日志和延迟消息）
     * @return 激活结果（success + 提示信息 + 卡信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> activate(Long membershipId, Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (membershipId == null) {
            data.put("success", false);
            data.put("message", "会员卡ID不能为空");
            return data;
        }

        Membership m = membershipMapper.findById(membershipId).orElse(null);
        if (m == null) {
            data.put("success", false);
            data.put("message", "会员卡不存在");
            return data;
        }

        // 校验归属（用户端激活时 userId 非空，管理员端 userId 为 null 跳过校验）
        if (userId != null && !userId.equals(m.getUserId())) {
            data.put("success", false);
            data.put("message", "无权操作该会员卡");
            return data;
        }

        if (!"UNACTIVATED".equals(m.getStatus())) {
            data.put("success", false);
            data.put("message", "该会员卡当前状态不支持激活（" + m.getStatus() + "）");
            return data;
        }

        // 锁定读（FOR UPDATE）取该用户全部 ACTIVE 卡，直接用其当前读结果做冲突判定。
        // 不能在锁后再用普通 SELECT：可重复读下一致性读沿用旧快照，会漏看并发事务刚提交的生效卡。
        List<Membership> activeCards = membershipMapper.findActiveByUserIdForUpdate(m.getUserId());

        Membership conflict = findConflict(m, activeCards);
        if (conflict != null) {
            String message = "已有生效中的" + categoryText(effectiveCategory(m))
                    + "「" + cardName(conflict) + "」（" + scopeText(conflict)
                    + "），同一范围同一时间仅允许一张生效卡";
            log.info("会员卡{}激活被拒：与生效卡{}范围冲突（userId={}）",
                    m.getId(), conflict.getId(), m.getUserId());
            data.put("success", false);
            data.put("message", message);
            return data;
        }

        // 解析卡类型有效天数
        Integer durationDays = resolveDurationDays(m);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = membershipExpirePolicy.endTime(now, durationDays);

        int rows = membershipMapper.activate(m.getId(), now, endTime);
        if (rows <= 0) {
            data.put("success", false);
            data.put("message", "激活失败，请稍后重试");
            return data;
        }

        String cardName = cardName(m);
        // 历史卡无 duration_days 时 endTime 为 null（有效期不定），文案兜底避免 NPE
        String endTimeText = endTime != null ? TIME_FMT.format(endTime) : "长期有效";
        log.info("会员卡{}已激活（userId={}，分类={}，范围={}，到期={}，{}）",
                m.getId(), m.getUserId(), effectiveCategory(m), scopeText(m), endTimeText,
                membershipExpirePolicy.describe());

        scheduleExpireEvents(m, cardName, now, endTime);

        // 关键通知短信：激活成功（异步发送，失败不影响激活主流程）
        smsService.notifyActivateSuccess(m.getUserId(), cardName, endTimeText,
                String.valueOf(m.getId()));

        data.put("success", true);
        data.put("message", "会员卡激活成功");
        data.put("membershipId", m.getId());
        data.put("cardName", cardName);
        data.put("startTime", TIME_FMT.format(now));
        data.put("endTime", endTimeText);
        data.put("durationDays", durationDays);
        return data;
    }

    /**
     * 激活成功后按 end_time 动态调度到期相关延迟消息：
     * 到期前 7 天/1 天提醒（剩余有效期不足对应提前量则不发）+ 到期消息（始终发送）。
     * 历史卡无法确定到期时间（endTime=null）时不调度任何消息。
     */
    private void scheduleExpireEvents(Membership m, String cardName,
                                      LocalDateTime now, LocalDateTime endTime) {
        if (endTime == null) {
            return;
        }
        Long remind7 = membershipExpirePolicy.remindDelayMs(now, endTime,
                MembershipExpirePolicy.REMIND_BEFORE_DAYS_7);
        if (remind7 != null) {
            membershipExpireProducer.schedule(m.getId(), m.getUserId(), cardName,
                    RabbitMQConfig.KIND_REMIND_7, remind7);
        }
        Long remind1 = membershipExpirePolicy.remindDelayMs(now, endTime,
                MembershipExpirePolicy.REMIND_BEFORE_DAYS_1);
        if (remind1 != null) {
            membershipExpireProducer.schedule(m.getId(), m.getUserId(), cardName,
                    RabbitMQConfig.KIND_REMIND_1, remind1);
        }
        membershipExpireProducer.schedule(m.getId(), m.getUserId(), cardName,
                RabbitMQConfig.KIND_EXPIRE, membershipExpirePolicy.expireDelayMs(now, endTime));
    }

    /**
     * 在同分类 ACTIVE 卡中查找适用范围有交集的冲突卡；无冲突返回 null
     */
    private Membership findConflict(Membership candidate, List<Membership> activeCards) {
        String category = effectiveCategory(candidate);
        String scope = effectiveScope(candidate);
        for (Membership active : activeCards) {
            if (!category.equals(effectiveCategory(active))) {
                continue;
            }
            if (scopeOverlap(scope, candidate.getStoreId(),
                    effectiveScope(active), active.getStoreId())) {
                return active;
            }
        }
        return null;
    }

    /**
     * 两张同分类卡的适用范围是否有交集：
     * 任一为全店通用即交集；均为单店时仅 storeId 相同才交集
     */
    private boolean scopeOverlap(String scope1, Long storeId1, String scope2, Long storeId2) {
        if (SCOPE_ALL.equals(scope1) || SCOPE_ALL.equals(scope2)) {
            return true;
        }
        return Objects.equals(storeId1, storeId2);
    }

    /** 历史无 card_type_id 的卡按普通卡处理 */
    private String effectiveCategory(Membership m) {
        return m.getCategory() != null ? m.getCategory() : CATEGORY_NORMAL;
    }

    /** 历史无 scope 的卡按全店通用处理 */
    private String effectiveScope(Membership m) {
        return m.getScope() != null ? m.getScope() : SCOPE_ALL;
    }

    private String categoryText(String category) {
        return CATEGORY_PT.equals(category) ? "私教课卡" : "普通卡";
    }

    private String scopeText(Membership m) {
        if (SCOPE_ALL.equals(effectiveScope(m))) {
            return "全店通用";
        }
        if (m.getStoreName() != null && !m.getStoreName().isBlank()) {
            return m.getStoreName();
        }
        return "指定门店";
    }

    private String cardName(Membership m) {
        return m.getCardTypeName() != null ? m.getCardTypeName()
                : (m.getCardType() != null ? m.getCardType() : "会员卡");
    }

    /**
     * 解析会员卡有效天数：优先 card_type_id 关联 card_types.duration_days
     */
    private Integer resolveDurationDays(Membership m) {
        if (m.getCardTypeId() != null) {
            CardType ct = cardTypeMapper.findById(m.getCardTypeId());
            if (ct != null) {
                return ct.getDurationDays();
            }
        }
        // 兜底：历史枚举卡类型按名称映射天数
        String type = m.getCardType();
        if (type == null) return null;
        switch (type) {
            case "月卡": case "MONTHLY": return 30;
            case "季卡": return 90;
            case "半年卡": return 180;
            case "年卡": case "YEARLY": return 365;
            default: return null;
        }
    }
}
