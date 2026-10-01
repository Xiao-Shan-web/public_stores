package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员卡实体（对应 memberships 表）
 */
@Data
public class Membership {

    /** 会员卡ID */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 会员卡号 */
    private String cardNo;

    /** 卡类型（历史枚举 TIMES/MONTHLY/YEARLY；新购卡写入卡类型名称如 月卡/季卡） */
    private String cardType;

    /** 卡类型ID（关联 card_types.id，新购卡写入） */
    private Long cardTypeId;

    /** 购卡时绑定的门店快照（ALL_STORE 卡为 NULL） */
    private Long storeId;

    /** 卡类型名称（LEFT JOIN card_types 查出，仅查询/展示用） */
    private String cardTypeName;

    /** 卡种分类：NORMAL-普通会员卡，PT-私教卡（JOIN card_types 查出） */
    private String category;

    /** 适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店（JOIN card_types 查出） */
    private String scope;

    /** 门店名称（LEFT JOIN stores 查出，仅展示用） */
    private String storeName;

    /** 总次数（次卡适用） */
    private Integer totalTimes;

    /** 剩余次数（次卡适用） */
    private Integer remainingTimes;

    /** 生效时间 */
    private LocalDateTime startTime;

    /** 到期时间 */
    private LocalDateTime endTime;

    /** 状态：UNACTIVATED-未激活（新购未首次到店），ACTIVE-有效，EXPIRED-已过期，DISABLED-已停用 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
