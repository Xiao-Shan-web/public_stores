package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.mapper.CardOrderMapper;
import com.xiaoshan.fitness.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 订单与优惠券状态联动兜底任务。
 * <p>
 * 覆盖两类"状态不同步"的残留，保证订单与券最终一致：
 * <ol>
 *   <li><b>超时未支付订单</b>：下单时券会从 UNUSED 置为 LOCKED 并绑定 order_id，若用户一直不支付，
 *       订单会永远停在 PENDING、券永远停在 LOCKED。本任务定时把超时订单置为 CANCELLED 并释放券
 *       （未过期归还 UNUSED，已过期置 EXPIRED）。不论订单是否占用券都会取消，保证订单状态自身闭环。</li>
 *   <li><b>孤儿锁定券补偿</b>：券处于 LOCKED，但关联订单已不是 PENDING（已支付/取消/退款）或订单缺失。
 *       这类券是异常路径（如历史上未做事务保证的中间态）的残留，本任务按订单终态补偿：
 *       订单 PAID → 券补记为 USED；订单 CANCELLED/REFUNDED/缺失 → 释放券。</li>
 * </ol>
 * 参数 {@code fitness.order.pay-timeout-minutes}（默认 15 分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScanTask {

    private final CardOrderMapper cardOrderMapper;
    private final CouponMapper couponMapper;
    private final CouponService couponService;

    /** 支付超时分钟数：超过该时长未支付的 PENDING 订单自动取消并释放券 */
    @Value("${fitness.order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    /** 扫描间隔 5 分钟（兜底任务，精度要求不高） */
    private static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000L;

    @Scheduled(fixedRate = SWEEP_INTERVAL_MS, initialDelay = 2 * 60 * 1000L)
    public void sweep() {
        closeTimeoutOrders();
        repairOrphanLockedCoupons();
    }

    /**
     * 关闭超时未支付订单，并释放其占用的优惠券。
     */
    private void closeTimeoutOrders() {
        List<Map<String, Object>> orders;
        try {
            orders = cardOrderMapper.findTimeoutPending(payTimeoutMinutes);
        } catch (Exception e) {
            log.error("超时订单扫描查询失败（timeout={}分钟），等下一轮重试", payTimeoutMinutes, e);
            return;
        }
        if (orders == null || orders.isEmpty()) {
            return;
        }

        int closed = 0;
        for (Map<String, Object> o : orders) {
            try {
                Long orderId = toLong(o.get("id"));
                Long userCouponId = toLong(o.get("userCouponId"));
                if (orderId == null) continue;
                int rows = cardOrderMapper.cancelTimeoutOrder(orderId);
                if (rows <= 0) {
                    continue; // 已被支付/手动取消
                }
                couponService.release(userCouponId, orderId);
                closed++;
            } catch (Exception e) {
                // 单笔异常不中断整轮，下一轮重试（释放动作幂等）
                log.error("超时订单处理失败：{}", o, e);
            }
        }
        if (closed > 0) {
            log.info("超时未支付订单兜底完成：关闭 {} 笔并释放占用券（超时阈值 {} 分钟）", closed, payTimeoutMinutes);
        }
    }

    /**
     * 补偿：LOCKED 券但关联订单已非 PENDING（或订单缺失）的状态不同步残留。
     */
    private void repairOrphanLockedCoupons() {
        List<Map<String, Object>> orphans;
        try {
            orphans = couponMapper.findOrphanLockedCoupons();
        } catch (Exception e) {
            log.error("孤儿锁定券扫描失败，等下一轮重试", e);
            return;
        }
        if (orphans == null || orphans.isEmpty()) {
            return;
        }

        int repaired = 0;
        for (Map<String, Object> c : orphans) {
            try {
                Long userCouponId = toLong(c.get("userCouponId"));
                Long orderId = toLong(c.get("orderId"));
                String orderStatus = c.get("orderStatus") == null ? null : String.valueOf(c.get("orderStatus"));
                if (userCouponId == null) continue;

                if ("PAID".equals(orderStatus) && orderId != null) {
                    // 订单已支付：券应核销，补记为 USED
                    couponMapper.markUsed(userCouponId, orderId);
                } else {
                    // 订单已取消/退款/缺失：释放券（未过期归还，已过期作废）
                    couponService.release(userCouponId, orderId);
                }
                repaired++;
            } catch (Exception e) {
                log.error("孤儿锁定券补偿失败：{}", c, e);
            }
        }
        if (repaired > 0) {
            log.info("优惠券状态联动补偿完成：修复 {} 张长期未同步的锁定券", repaired);
        }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
