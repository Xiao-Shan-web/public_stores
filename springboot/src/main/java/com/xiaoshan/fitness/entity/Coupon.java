package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板（对应 coupons 表）
 */
@Data
public class Coupon {

    /** 券ID */
    private Long id;

    /** 券名称 */
    private String name;

    /** 类型：FULL_REDUCE-满减，DIRECT-直减，DISCOUNT-折扣 */
    private String type;

    /** 使用门槛（订单金额需 >= 该值；0 表示无门槛） */
    private java.math.BigDecimal threshold;

    /** 抵扣面额（FULL_REDUCE/DIRECT 使用；DISCOUNT 恒为 0） */
    private java.math.BigDecimal amount;

    /** 折扣率（DISCOUNT 使用，0.85 表示 85 折） */
    private java.math.BigDecimal discount;

    /** 最高优惠上限（DISCOUNT 使用，null 表示不封顶） */
    private java.math.BigDecimal maxDiscount;

    /** 发行总量（0 表示不限量） */
    private Integer totalCount;

    /** 已领取数量 */
    private Integer issuedCount;

    /** 每人限领张数 */
    private Integer perUserLimit;

    /** 有效期类型：FIXED-固定区间，DAYS_AFTER_RECEIVE-领取后N天 */
    private String validType;

    /** 固定有效期开始 */
    private LocalDateTime startTime;

    /** 固定有效期结束 */
    private LocalDateTime endTime;

    /** 领取后有效天数 */
    private Integer validDays;

    /** 状态：1-上架可领，0-下架 */
    private Integer status;

    /** 是否删除：0-否，1-是 */
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
