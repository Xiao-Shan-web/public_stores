package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限时活动（对应 activities 表）
 * 活动价 = 卡类型原价 × discount（折扣率，0.90 表示 9 折）
 */
@Data
public class Activity {

    /** 活动ID */
    private Long id;

    /** 活动标题 */
    private String title;

    /** 活动副标题 */
    private String subtitle;

    /** 活动封面图 */
    private String coverUrl;

    /** 活动详情 */
    private String content;

    /** 适用卡类型ID */
    private Long cardTypeId;

    /** 折扣率（0.90 表示 9 折） */
    private BigDecimal discount;

    /** 活动名额（null 不限） */
    private Integer quotaTotal;

    /** 已售名额 */
    private Integer quotaUsed;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 状态：1-上架，0-下架 */
    private Integer status;

    /** 是否删除：0-否，1-是 */
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
