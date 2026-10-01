package com.xiaoshan.fitness.vo;

/**
 * 用户端卡类型视图对象（在 {@link CardTypeVO} 基础上附带限时活动价格信息）
 * <p>
 * 该对象为<b>请求级实时组装</b>，不进入 Redis 缓存：缓存中只存基础卡信息，
 * 活动字段每次请求按 activities 表实时计算，保证管理端上下架 / 活动到期 /
 * 名额售罄后用户端立即恢复原价。
 * <p>
 * 价格一律为 String（BigDecimal.toPlainString），避免前端精度丢失。
 * 无进行中活动时 activityXxx 字段全部为 null，前端只展示原价。
 */
public class UserCardTypeVO extends CardTypeVO {

    /** 原价（卡类型挂牌价，等同 price） */
    private String originalPrice;

    /** 活动价（原价 × 折扣，HALF_UP 保留两位；无活动为 null） */
    private String activityPrice;

    /** 适用的活动 ID（无活动为 null） */
    private Long activityId;

    /** 活动标题（无活动为 null） */
    private String activityTitle;

    /** 活动结束时间（无活动为 null） */
    private java.time.LocalDateTime activityEndTime;

    /** 活动折扣（如 0.8 表示 8 折，用于前端展示"限时 8 折"标签；无活动为 null） */
    private String discount;

    public String getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(String originalPrice) { this.originalPrice = originalPrice; }

    public String getActivityPrice() { return activityPrice; }
    public void setActivityPrice(String activityPrice) { this.activityPrice = activityPrice; }

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }

    public String getActivityTitle() { return activityTitle; }
    public void setActivityTitle(String activityTitle) { this.activityTitle = activityTitle; }

    public java.time.LocalDateTime getActivityEndTime() { return activityEndTime; }
    public void setActivityEndTime(java.time.LocalDateTime activityEndTime) { this.activityEndTime = activityEndTime; }

    public String getDiscount() { return discount; }
    public void setDiscount(String discount) { this.discount = discount; }

}
