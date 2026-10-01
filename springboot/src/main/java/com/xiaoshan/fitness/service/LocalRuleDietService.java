package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.util.NutritionCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 本地规则生成器（表单接口默认 Provider）
 * <p>
 * 算法统一收口到 {@link NutritionCalculator}（Mifflin-St Jeor + 活动系数 + 目标调整 + 带克数食谱），
 * 本类固定使用中等活动系数（1.55），与 AI 对话流程共用同一套精确计算。
 */
@Service
@Slf4j
public class LocalRuleDietService implements AiDietService {

    private static final String PROVIDER = "LOCAL";

    @Override
    public Map<String, Object> generatePlan(Integer height, Integer weight, Integer age,
                                            String gender, String goal) {
        Map<String, Object> data = NutritionCalculator.generate(height, weight, age, gender, goal, "MODERATE");
        data.put("provider", PROVIDER);
        log.info("本地规则生成计划：身高{}cm 体重{}kg 目标{} → 每日{}kcal",
                height, weight, goal, data.get("dailyCalories"));
        return data;
    }

    @Override
    public Map<String, Object> buildMeal(String type, List<String> foods, int calories) {
        return NutritionCalculator.buildMeal(type, foods, calories);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

}
