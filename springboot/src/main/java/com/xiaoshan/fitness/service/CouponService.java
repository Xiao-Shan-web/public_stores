package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.CardOrder;
import com.xiaoshan.fitness.entity.Coupon;
import com.xiaoshan.fitness.mapper.CardOrderMapper;
import com.xiaoshan.fitness.mapper.CouponMapper;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.CouponCalculator;
import com.xiaoshan.fitness.util.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 优惠券服务
 * <p>
 * 领取链路双保险：
 * 1. Redis SETNX 短锁（用户维度）→ 拦截连点/并发重复提交，降低 DB 压力
 * 2. DB 层 {@code issued_count < total_count} 条件更新 + 每人限领计数 → 最终一致，绝不超发
 * <p>
 * 券生命周期：领取(UNUSED) → 下单锁定(LOCKED) → 支付核销(USED)
 *                          下单取消/超时/用户主动释放(LOCKED → UNUSED 或 EXPIRED)
 *                          订单退款(USED → UNUSED 或 EXPIRED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    /** 券状态 */
    public static final String ST_UNUSED = "UNUSED";
    public static final String ST_LOCKED = "LOCKED";
    public static final String ST_USED = "USED";
    public static final String ST_EXPIRED = "EXPIRED";

    private final CouponMapper couponMapper;
    private final CardOrderMapper cardOrderMapper;
    private final CouponIssuer couponIssuer;
    private final DistributedLock distributedLock;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 用户领取优惠券
     *
     * @param userId   用户ID
     * @param couponId 券模板ID
     * @return 领取到的用户券信息
     */
    public Map<String, Object> receive(Long userId, Long couponId) {
        // 1. Redis 短锁防连点（同一用户同一券 3 秒内只处理一次）
        String lockKey = "coupon:receive:" + userId + ":" + couponId;
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(429, "手速太快啦，请稍后重试");
        }

        try {
            Coupon c = couponMapper.findById(couponId);
            if (c == null || (c.getIsDeleted() != null && c.getIsDeleted() == 1)) {
                throw new BusinessException(404, "优惠券不存在");
            }
            if (c.getStatus() == null || c.getStatus() != 1) {
                throw new BusinessException(400, "该优惠券已下架");
            }
            if (c.getTotalCount() != null && c.getTotalCount() > 0
                    && c.getIssuedCount() != null && c.getIssuedCount() >= c.getTotalCount()) {
                throw new BusinessException(400, "优惠券已被领完");
            }
            // 每人限领校验
            long owned = couponMapper.countUserCoupon(couponId, userId);
            int limit = c.getPerUserLimit() == null ? 1 : c.getPerUserLimit();
            if (owned >= limit) {
                throw new BusinessException(400, "您已领取过该优惠券（每人限领 " + limit + " 张）");
            }

            // 发券走独立 Bean，保证 @Transactional 生效（计数 +1 与发放记录同成功）
            return couponIssuer.issue(userId, c);
        } finally {
            // 短锁提前释放，允许用户后续操作（限领由 DB 计数兜底）
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 下单时锁定券（UNUSED → LOCKED），并返回可用于抵扣的金额
     * <p>
     * 校验：归属本人、状态可用、未过期、订单金额满足门槛；
     * 抵扣金额统一由 {@link CouponCalculator} 计算（满减/直减/折扣+封顶口径一致）。
     *
     * @return 抵扣金额（不超过订单金额）
     */
    public BigDecimal lockForOrder(Long userId, Long userCouponId, Long orderId, BigDecimal orderAmount) {
        Map<String, Object> uc = couponMapper.findUserCouponDetail(userCouponId);
        if (uc == null) {
            throw new BusinessException(404, "优惠券不存在");
        }
        Object owner = uc.get("userId");
        if (owner == null || !String.valueOf(userId).equals(String.valueOf(((Number) owner).longValue()))) {
            throw new BusinessException(400, "无权使用该优惠券");
        }
        if (!ST_UNUSED.equals(String.valueOf(uc.get("status")))) {
            throw new BusinessException(400, "该优惠券不可用（已被使用或锁定）");
        }

        String type = uc.get("couponType") == null ? null : String.valueOf(uc.get("couponType"));
        BigDecimal threshold = toDecimal(uc.get("threshold"));
        BigDecimal face = toDecimal(uc.get("amount"));
        BigDecimal discount = uc.get("discount") == null ? null : toDecimal(uc.get("discount"));
        BigDecimal maxDiscount = uc.get("maxDiscount") == null ? null : toDecimal(uc.get("maxDiscount"));

        BigDecimal cut = CouponCalculator.cutAmount(type, face, discount, maxDiscount, threshold, orderAmount);
        if (cut.compareTo(BigDecimal.ZERO) <= 0) {
            String reason = CouponCalculator.unavailableReason(threshold, orderAmount);
            throw new BusinessException(400, reason != null ? reason + "，无法使用该券" : "该优惠券当前不可用于本订单");
        }

        // 状态流转（带前置条件，防并发重复占用）
        int rows = couponMapper.lockForOrder(userCouponId, userId, orderId);
        if (rows == 0) {
            throw new BusinessException(400, "该优惠券已被其他订单占用，请重新选择");
        }
        return cut;
    }

    /**
     * 支付成功后核销券（LOCKED → USED）
     */
    public void markUsed(Long userCouponId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        int rows = couponMapper.markUsed(userCouponId, orderId);
        if (rows == 0) {
            // 券状态异常（可能已被释放）不应阻塞支付主流程，仅告警
            log.warn("订单{}支付成功但券{}核销未命中（状态可能已变更）", orderId, userCouponId);
        } else {
            log.info("订单{}核销优惠券{}", orderId, userCouponId);
        }
    }

    /**
     * 订单取消/超时：释放券（未过期归还 UNUSED，已过期置 EXPIRED）
     *
     * @param orderId 占用来源订单ID；为空时按券自身有效期直接回收（兜底异常锁定券）
     */
    public void release(Long userCouponId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        if (orderId == null) {
            // 异常锁定券（无占用订单）：按有效期一次性回收
            if (couponMapper.releaseLockById(userCouponId) > 0) {
                log.info("优惠券{}无关联订单，已按有效期回收", userCouponId);
            }
            return;
        }
        int back = couponMapper.releaseLock(userCouponId, orderId);
        if (back > 0) {
            log.info("订单{}取消，优惠券{}已归还用户", orderId, userCouponId);
            return;
        }
        int expired = couponMapper.expireLocked(userCouponId, orderId);
        if (expired > 0) {
            log.info("订单{}取消，优惠券{}锁定时已过期，置为 EXPIRED", orderId, userCouponId);
        }
    }

    /**
     * 用户主动释放被订单占用的券（LOCKED → UNUSED/EXPIRED）。
     * <p>
     * 订单与券的联动是双向的：若占用来源订单仍是 PENDING，则一并取消该订单，
     * 保证「订单 CANCELLED ⇄ 券 UNUSED + order_id 清空」始终一致，避免券被长期占用。
     *
     * @return 释放后券的新状态（UNUSED / EXPIRED）
     */
    public String releaseByCoupon(Long userId, Long userCouponId) {
        Map<String, Object> uc = couponMapper.findUserCouponDetail(userCouponId);
        if (uc == null) {
            throw new BusinessException(404, "优惠券不存在");
        }
        Object owner = uc.get("userId");
        if (owner == null || !String.valueOf(userId).equals(String.valueOf(((Number) owner).longValue()))) {
            throw new BusinessException(400, "无权操作该优惠券");
        }
        if (!ST_LOCKED.equals(String.valueOf(uc.get("status")))) {
            throw new BusinessException(400, "该优惠券当前不是占用状态");
        }

        Long orderId = uc.get("orderId") == null ? null : ((Number) uc.get("orderId")).longValue();
        if (orderId != null) {
            CardOrder order = cardOrderMapper.findById(orderId);
            // 订单已支付但券仍为 LOCKED 属异常残留（正常核销在支付事务内完成），
            // 此时不能把券退回用户，交由 OrderTimeoutScanTask 补偿为 USED。
            if (order != null && "PAID".equals(order.getStatus())) {
                throw new BusinessException(409, "关联订单已支付，优惠券将自动核销，请稍后刷新");
            }
            // 取消占用的待支付订单；订单已取消/退款时该更新自然为 0 行，不影响后续释放
            cardOrderMapper.cancelPending(orderId);
        }
        release(userCouponId, orderId);

        Map<String, Object> after = couponMapper.findUserCouponDetail(userCouponId);
        return after == null ? ST_UNUSED : String.valueOf(after.get("status"));
    }

    /**
     * 订单退款：USED → UNUSED（券未过期）/ EXPIRED（券已过期）。
     * 业务规则：退款成功时优惠券若仍在有效期内则退回用户，已过期则作废。
     */
    public void refund(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            return;
        }
        if (couponMapper.releaseUsed(userCouponId, orderId) > 0) {
            log.info("订单{}退款，优惠券{}未过期已退回", orderId, userCouponId);
            return;
        }
        if (couponMapper.expireUsed(userCouponId, orderId) > 0) {
            log.info("订单{}退款，优惠券{}已过期作废", orderId, userCouponId);
        }
    }

    /** 用户券列表 */
    public List<Map<String, Object>> listUserCoupons(Long userId, String scope) {
        String s = (scope == null || scope.isBlank()) ? "UNUSED" : scope.toUpperCase();
        return couponMapper.findUserCoupons(userId, s);
    }

    // ==================== 私有工具 ====================

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

}
