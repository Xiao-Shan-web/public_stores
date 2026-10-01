package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持有的优惠券（对应 user_coupons 表）
 * <p>
 * 状态机：UNUSED（可用）→ LOCKED（下单占用）→ USED（已核销）
 * LOCKED → UNUSED（订单取消/超时自动取消/用户主动释放）；LOCKED/UNUSED → EXPIRED（过期）
 * USED → UNUSED（订单退款且券未过期）；USED → EXPIRED（订单退款但券已过期）
 */
@Data
public class UserCoupon {

    /** 用户券ID */
    private Long id;

    /** 券模板ID */
    private Long couponId;

    /** 持有人ID */
    private Long userId;

    /** 状态：UNUSED / LOCKED / USED / EXPIRED */
    private String status;

    /** 关联订单ID（锁定/核销时写入） */
    private Long orderId;

    /** 生效时间 */
    private LocalDateTime startTime;

    /** 失效时间 */
    private LocalDateTime endTime;

    /** 核销时间 */
    private LocalDateTime usedAt;

    /** 领取时间 */
    private LocalDateTime createdAt;

}
