package com.xiaoshan.fitness.service;

import java.util.List;
import java.util.Map;

/**
 * AI 饮食计划生成服务
 * <p>
 * 设计为可插拔 Provider：
 * - LOCAL：本地规则（BMR 公式 + 食物库），无需 API Key 即可工作
 * - OPENAI / QWEN：远程大模型，后续配置 Key 后切换
 */
public interface AiDietService {

    /**
     * 生成饮食计划
     *
     * @param height 身高 cm
     * @param weight 体重 kg
     * @param age    年龄
     * @param gender MALE / FEMALE
     * @param goal   MUSCLE_GAIN / FAT_LOSS / MAINTAIN
     * @return 计划结果：dailyCalories / protein / carbs / fat / meals / provider
     */
    Map<String, Object> generatePlan(Integer height, Integer weight, Integer age,
                                      String gender, String goal);

    /**
     * 餐次结构
     */
    Map<String, Object> buildMeal(String type, List<String> foods, int calories);

    /** 标识当前 Provider（LOCAL/OPENAI/QWEN） */
    String provider();

}
