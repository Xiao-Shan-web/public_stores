package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购买订单实体（对应 card_orders 表）
 */
@Data
public class CardOrder {

    /** 订单ID（雪花算法生成） */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 第三方交易号（支付宝 trade_no） */
    private String tradeNo;

    /** 下单用户ID */
    private Long userId;

    /** 关联会员卡ID（支付成功后生成） */
    private Long membershipId;

    /** 购买的卡类型名称（冗余，便于展示） */
    private String cardType;

    /** 购买的卡类型ID（关联 card_types.id） */
    private Long cardTypeId;

    /** 参与的活动ID（活动价下单时有值） */
    private Long activityId;

    /** 使用的用户券ID */
    private Long userCouponId;

    /** 券抵扣金额 */
    private BigDecimal discountAmount;

    /** 原价（活动价前；无活动时等于 amount） */
    private BigDecimal originalAmount;

    /** 订单金额（实付：原价 - 券抵扣） */
    private BigDecimal amount;

    /** 订单状态：PENDING-待支付，PAID-已支付，CANCELLED-已取消（含超时自动取消），REFUNDED-已退款 */
    private String status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 实际支付时间（以第三方回调为准） */
    private LocalDateTime paidAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
