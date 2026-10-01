package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.CardOrder;
import com.xiaoshan.fitness.entity.CardType;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.mapper.ActivityMapper;
import com.xiaoshan.fitness.mapper.CardOrderMapper;
import com.xiaoshan.fitness.mapper.CardTypeMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付成功处理服务（供 MockPayService / AlipayController 共用）
 * <p>
 * 职责：
 *   1. 生成 UNACTIVATED 会员卡（写入购卡门店快照；私教课卡写入总节数/剩余节数）
 *   2. 更新订单状态为 PAID（写入 tradeNo + paidAt）
 * <p>
 * 注意：购买时<b>不</b>发送 RabbitMQ 延迟消息；到期提醒/过期消息统一在
 * {@link MembershipActivationService} 激活成功后调度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String CATEGORY_PT = "PT";

    private final CardOrderMapper cardOrderMapper;
    private final CardTypeMapper cardTypeMapper;
    private final MembershipMapper membershipMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final CouponService couponService;
    private final ActivityMapper activityMapper;
    private final SmsService smsService;

    /**
     * 支付成功：生成会员卡 + 更新订单
     *
     * @param order   支付成功的订单
     * @param tradeNo 第三方交易号（Mock 模式传 null）
     * @return 生成的会员卡 ID；若订单已处理过则返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long handlePaid(CardOrder order, String tradeNo) {
        // 查卡类型（门店与节数快照来源）
        CardType ct = order.getCardTypeId() != null ? cardTypeMapper.findById(order.getCardTypeId()) : null;

        // 1. 生成 UNACTIVATED 会员卡
        Membership m = new Membership();
        m.setId(idGenerator.nextId());
        m.setUserId(order.getUserId());
        m.setCardNo("MBR" + idGenerator.nextId());
        m.setCardType(order.getCardType());
        m.setCardTypeId(order.getCardTypeId());
        // 购卡门店快照：单店卡记绑定门店，全店通用卡为 null
        m.setStoreId(ct != null ? ct.getStoreId() : null);
        if (ct != null && CATEGORY_PT.equals(ct.getCategory()) && ct.getTotalTimes() != null) {
            // 私教课卡：写入总节数，剩余节数初始等于总节数
            m.setTotalTimes(ct.getTotalTimes());
            m.setRemainingTimes(ct.getTotalTimes());
        } else {
            // 普通卡：按天计费，不写次数
            m.setTotalTimes(null);
            m.setRemainingTimes(null);
        }
        m.setStartTime(null);     // 未激活，激活时设置
        m.setEndTime(null);
        m.setStatus("UNACTIVATED");
        membershipMapper.insert(m);

        // 2. 更新订单为 PAID（乐观锁：仅 PENDING 可更新，防重复支付）
        int rows = cardOrderMapper.updatePaid(order.getId(), "PAID", LocalDateTime.now(),
                LocalDateTime.now(), m.getId(), tradeNo);
        if (rows == 0) {
            log.warn("订单{}已被其他流程处理过，跳过", order.getOrderNo());
            // 回滚：删除刚插入的 membership（但 DB 无反向 FK，用状态标记 DISABLED 即可）
            membershipMapper.disable(m.getId());
            return null;
        }

        log.info("订单{}支付成功，生成会员卡{}（UNACTIVATED，卡类型={}，门店={}，节数={}）",
                order.getOrderNo(), m.getId(), order.getCardType(), m.getStoreId(), m.getTotalTimes());

        // 3. 核销优惠券（LOCKED → USED）：放在主流程内，Mock 支付与支付宝回调两条路径自动覆盖
        couponService.markUsed(order.getUserCouponId(), order.getId());

        // 4. 占用活动名额（限时限量）：条件更新，超额/过期/下架时 rows=0，仅记录告警不影响支付
        if (order.getActivityId() != null) {
            int quotaRows = activityMapper.consumeQuota(order.getActivityId());
            if (quotaRows == 0) {
                log.warn("订单{}关联的活动{}名额已满或已结束，本次未占用名额",
                        order.getOrderNo(), order.getActivityId());
            }
        }

        // 5. 关键通知短信：支付成功（异步发送，失败不影响支付主流程）
        smsService.notifyPaySuccess(order.getUserId(), order.getOrderNo(),
                order.getAmount() == null ? "0" : order.getAmount().stripTrailingZeros().toPlainString(),
                order.getCardType());

        return m.getId();
    }

}
