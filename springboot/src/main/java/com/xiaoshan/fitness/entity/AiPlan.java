package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 饮食计划实体（对应 ai_plans 表）
 */
@Data
public class AiPlan {

    /** 计划ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 身高(cm) */
    private Integer height;

    /** 体重(kg) */
    private Integer weight;

    /** 年龄 */
    private Integer age;

    /** 性别：MALE/FEMALE */
    private String gender;

    /** 目标：MUSCLE_GAIN/FAT_LOSS/MAINTAIN */
    private String goal;

    /** 每日热量(kcal) */
    private Integer dailyCalories;

    /** 蛋白质(g) */
    private Integer protein;

    /** 碳水(g) */
    private Integer carbs;

    /** 脂肪(g) */
    private Integer fat;

    /** 三餐建议(JSON) */
    private String mealsJson;

    /** 生成源：LOCAL/OpenAI/QWEN */
    private String provider;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
