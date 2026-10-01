package com.xiaoshan.fitness.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠券金额计算器（全链路唯一口径）
 * <p>
 * 试算接口（/user/coupons/usable）、下单锁定（CouponService.lockForOrder）、
 * 前端展示均调用此处，保证「前端看到的抵扣 = 后端计算的抵扣 = 订单写入的抵扣」。
 * <p>
 * 支持类型：
 * <ul>
 *   <li>{@link #TYPE_FULL_REDUCE} 满减：订单金额 ≥ threshold 时可抵扣 amount；</li>
 *   <li>{@link #TYPE_DIRECT} 直减：无门槛（threshold 一般为 0）抵扣 amount；</li>
 *   <li>{@link #TYPE_DISCOUNT} 折扣：抵扣 = 订单金额 × (1 - discount)，
 *       且有 maxDiscount 封顶（如 8.5 折最高减 50 元）。</li>
 * </ul>
 * 所有结果均不超过订单金额（避免实付为负），保留 2 位小数（HALF_UP）。
 */
public final class CouponCalculator {

    public static final String TYPE_FULL_REDUCE = "FULL_REDUCE";
    public static final String TYPE_DIRECT = "DIRECT";
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    private CouponCalculator() {
    }

    /**
     * 校验券类型是否合法
     */
    public static boolean isValidType(String type) {
        return TYPE_FULL_REDUCE.equals(type) || TYPE_DIRECT.equals(type) || TYPE_DISCOUNT.equals(type);
    }

    /**
     * 是否满足使用门槛
     */
    public static boolean meetsThreshold(BigDecimal threshold, BigDecimal orderAmount) {
        if (orderAmount == null) return false;
        return orderAmount.compareTo(nz(threshold)) >= 0;
    }

    /**
     * 计算抵扣金额（不满足门槛或参数非法时返回 0）
     *
     * @param type        券类型
     * @param faceValue   抵扣面额（FULL_REDUCE / DIRECT）
     * @param discount    折扣率（DISCOUNT，0.85 表示 85 折）
     * @param maxDiscount 最高优惠上限（DISCOUNT，null 不封顶）
     * @param threshold   使用门槛
     * @param orderAmount 订单金额（应用优惠前）
     */
    public static BigDecimal cutAmount(String type, BigDecimal faceValue, BigDecimal discount,
                                       BigDecimal maxDiscount, BigDecimal threshold, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return zero();
        }
        if (!meetsThreshold(threshold, orderAmount)) {
            return zero();
        }
        BigDecimal cut;
        if (TYPE_DISCOUNT.equals(type)) {
            BigDecimal rate = nz(discount);
            // 折扣率必须落在 (0,1) 区间，否则视为无效券
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                return zero();
            }
            cut = orderAmount.multiply(BigDecimal.ONE.subtract(rate));
            BigDecimal cap = maxDiscount;
            if (cap != null && cap.compareTo(BigDecimal.ZERO) > 0 && cut.compareTo(cap) > 0) {
                cut = cap;
            }
        } else {
            // FULL_REDUCE / DIRECT：直减面额
            cut = nz(faceValue);
        }
        if (cut.compareTo(BigDecimal.ZERO) <= 0) {
            return zero();
        }
        // 抵扣不超过订单金额
        return cut.min(orderAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 不可用原因（可用时返回 null），用于下单页置灰提示
     */
    public static String unavailableReason(BigDecimal threshold, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "订单金额异常";
        }
        BigDecimal t = nz(threshold);
        if (orderAmount.compareTo(t) < 0) {
            return "满 " + t.stripTrailingZeros().toPlainString() + " 元可用";
        }
        return null;
    }

    /**
     * 券面额展示文案：折扣券返回「8.5折」或「8.5折(最高减50)」，其余返回「¥X」
     */
    public static String faceText(String type, BigDecimal faceValue, BigDecimal discount, BigDecimal maxDiscount) {
        if (TYPE_DISCOUNT.equals(type)) {
            BigDecimal rate = nz(discount);
            if (rate.compareTo(BigDecimal.ZERO) > 0 && rate.compareTo(BigDecimal.ONE) < 0) {
                String fold = rate.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
                BigDecimal cap = maxDiscount;
                if (cap != null && cap.compareTo(BigDecimal.ZERO) > 0) {
                    return fold + "折(最高减" + cap.stripTrailingZeros().toPlainString() + ")";
                }
                return fold + "折";
            }
        }
        return "¥" + nz(faceValue).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
