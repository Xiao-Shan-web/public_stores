package com.xiaoshan.fitness.service;

import lombok.Data;

import java.util.List;

/**
 * AI 对话会话上下文（Redis 存储，JSON 序列化，30 分钟滑动过期）
 */
@Data
public class AiChatSession {

    /** 会话归属用户ID */
    private Long userId;

    /** 当前收集步骤：WEIGHT/HEIGHT/AGE/GENDER/GOAL/ACTIVITY/DONE */
    private String step;

    /** 体重(kg) */
    private Integer weight;

    /** 身高(cm) */
    private Integer height;

    /** 年龄 */
    private Integer age;

    /** 性别：MALE/FEMALE */
    private String gender;

    /** 目标：MUSCLE_GAIN/FAT_LOSS/MAINTAIN */
    private String goal;

    /** 活动量：SEDENTARY/LIGHT/MODERATE/ACTIVE */
    private String activityLevel;

    /** 食谱轮换索引：同参数"换一份"时轮换不同食物组合（0-2） */
    private Integer variant;

    /** 用户指定的每日热量（kcal）：null 表示按 TDEE 自动计算 */
    private Integer targetCalories;

    /** "换一份"累计次数：区分首次换与连续换的回复 */
    private Integer regenCount;

    /** 最近一条 AI 回复：避免连续重复同一句话 */
    private String lastReply;

    /** 当前步骤连续校验失败次数（解析失败或范围异常时累加，成功时清零） */
    private Integer failureCount;

    /** 最近 3 条 AI 回复文案模板（3 轮内防复读） */
    private List<String> recentReplies;

}
