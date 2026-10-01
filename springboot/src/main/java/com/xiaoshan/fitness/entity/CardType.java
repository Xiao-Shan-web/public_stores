package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 健身卡类型实体（对应 card_types 表）
 * 管理端可维护卡名称、有效天数、价格、描述、启用状态
 * category：NORMAL-普通会员卡（月卡/季卡/半年卡/年卡），PT-私教会员卡
 */
@Data
public class CardType {

    /** 卡类型ID（雪花算法生成） */
    private Long id;

    /** 卡名称（如：月卡、季卡、年卡） */
    private String name;

    /** 分类：NORMAL-普通会员卡，PT-私教会员卡 */
    private String category;

    /** 适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店 */
    private String scope;

    /** 绑定门店ID（scope=SINGLE_STORE 时必填） */
    private Long storeId;

    /** 绑定门店名称（LEFT JOIN stores 查出，仅展示用） */
    private String storeName;

    /** 有效天数 */
    private Integer durationDays;

    /** 私教课总节数（category=PT 时必填，NORMAL 为空） */
    private Integer totalTimes;

    /** 价格 */
    private BigDecimal price;

    /** 卡描述 */
    private String description;

    /** 是否启用：1-启用，0-禁用 */
    private Integer isActive;

    /** 是否删除：0-未删除，1-已删除（软删除标记） */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
