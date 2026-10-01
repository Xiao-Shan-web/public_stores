package com.xiaoshan.fitness.vo;

import java.time.LocalDateTime;

/**
 * 卡类型视图对象（对管理端 / 用户端暴露的字段）
 * <p>
 * 可直接作为 Redis 缓存值：类型明确，Jackson 可无条件序列化/反序列化，
 * 不使用 Map&lt;String, Object&gt;（GenericJackson2JsonRedisSerializer 无法处理 Object 根值）。
 * <p>
 * price 保持 String（BigDecimal.toPlainString），避免前端精度丢失；不暴露 isDeleted。
 */
public class CardTypeVO {

    /** 卡类型ID */
    private Long id;

    /** 卡名称 */
    private String name;

    /** 分类：NORMAL-普通会员卡，PT-私教课卡 */
    private String category;

    /** 适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店 */
    private String scope;

    /** 绑定门店ID */
    private Long storeId;

    /** 绑定门店名称（仅展示） */
    private String storeName;

    /** 有效天数 */
    private Integer durationDays;

    /** 私教课总节数（NORMAL 为空） */
    private Integer totalTimes;

    /** 价格（字符串，避免前端精度丢失） */
    private String price;

    /** 卡描述 */
    private String description;

    /** 是否启用：1-启用，0-禁用 */
    private Integer isActive;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public Integer getTotalTimes() { return totalTimes; }
    public void setTotalTimes(Integer totalTimes) { this.totalTimes = totalTimes; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
