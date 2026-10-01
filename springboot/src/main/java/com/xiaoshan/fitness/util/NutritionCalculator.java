package com.xiaoshan.fitness.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养精确计算器
 * <p>
 * 大模型只负责自然语言对话与信息提取，TDEE、目标热量、三大营养素、
 * 食谱克数等全部由本类精确计算，不交给大模型。
 * <p>
 * 算法：
 * 1. BMR 用 Mifflin-St Jeor 公式
 * 2. TDEE = BMR × 活动系数（SEDENTARY 1.2 / LIGHT 1.375 / MODERATE 1.55 / ACTIVE 1.725）
 * 3. 目标调整：增肌 +400 / 减脂 -500 / 保持 0
 * 4. 三大营养素按每 kg 体重配比
 * 5. 餐次分配：早 25% / 午 35% / 晚 25% / 加餐 15%，食物按目标匹配并带克数
 */
public final class NutritionCalculator {

    private NutritionCalculator() {
    }

    /**
     * 活动等级 → 活动系数
     */
    public static double factorOf(String activityLevel) {
        if (activityLevel == null) {
            return 1.55;
        }
        switch (activityLevel) {
            case "SEDENTARY": return 1.2;
            case "LIGHT":     return 1.375;
            case "ACTIVE":    return 1.725;
            case "MODERATE":
            default:          return 1.55;
        }
    }

    /**
     * 生成完整饮食计划（食谱取第 0 套变体）
     *
     * @param height        身高 cm（100-250）
     * @param weight        体重 kg（30-300）
     * @param age           年龄（10-100）
     * @param gender        MALE / FEMALE
     * @param goal          MUSCLE_GAIN / FAT_LOSS / MAINTAIN
     * @param activityLevel SEDENTARY / LIGHT / MODERATE / ACTIVE
     * @return height/weight/age/gender/goal/activityLevel/dailyCalories/protein/carbs/fat/meals
     */
    public static Map<String, Object> generate(Integer height, Integer weight, Integer age,
                                               String gender, String goal, String activityLevel) {
        return generate(height, weight, age, gender, goal, activityLevel, 0);
    }

    /**
     * 生成完整饮食计划（指定食谱变体，同参数换 variant 可轮换不同食物组合）
     *
     * @param variant 食谱变体索引（0-2，超出范围自动取模轮换）
     */
    public static Map<String, Object> generate(Integer height, Integer weight, Integer age,
                                               String gender, String goal, String activityLevel, int variant) {
        return generate(height, weight, age, gender, goal, activityLevel, variant, null);
    }

    /**
     * 生成完整饮食计划（支持用户指定每日热量）
     *
     * @param targetCalories 用户指定的每日热量；为 null 时按 TDEE + 目标调整计算。
     *                       指定时蛋白质仍按体重保够（先算蛋白与脂肪，碳水用剩余热量倒推），
     *                       热量过低时压缩脂肪、保底碳水 50g。
     */
    public static Map<String, Object> generate(Integer height, Integer weight, Integer age,
                                               String gender, String goal, String activityLevel,
                                               int variant, Integer targetCalories) {
        validate(height, weight, age, gender, goal);
        if (activityLevel == null) {
            activityLevel = "MODERATE";
        }

        // 1. BMR（Mifflin-St Jeor）
        double bmr = 10 * weight + 6.25 * height - 5 * age;
        bmr = "MALE".equals(gender) ? bmr + 5 : bmr - 161;

        // 2. TDEE
        double tdee = bmr * factorOf(activityLevel);

        // 3. 目标热量：用户指定优先，否则按 TDEE + 目标调整
        int dailyCalories = targetCalories != null
                ? clampCalories(targetCalories)
                : calcTargetCalories(tdee, goal);

        // 4. 三大营养素
        double proteinPerKg, carbsPerKg, fatPerKg;
        switch (goal) {
            case "MUSCLE_GAIN":
                proteinPerKg = 2.0; carbsPerKg = 5.0; fatPerKg = 1.0;
                break;
            case "FAT_LOSS":
                proteinPerKg = 2.2; carbsPerKg = 2.5; fatPerKg = 0.8;
                break;
            default: // MAINTAIN
                proteinPerKg = 1.6; carbsPerKg = 4.0; fatPerKg = 1.0;
        }
        int protein;
        int carbs;
        int fat;
        if (targetCalories != null) {
            // 指定热量：蛋白质按体重保够，脂肪占 25%，碳水用剩余热量倒推
            protein = (int) Math.round(weight * proteinPerKg);
            fat = (int) Math.round(dailyCalories * 0.25 / 9.0);
            carbs = (int) Math.round((dailyCalories - (double) protein * 4 - fat * 9) / 4);
            if (carbs < 50) {
                // 热量过低：压缩脂肪，保底碳水 50g
                carbs = 50;
                fat = (int) Math.max(20, Math.round((dailyCalories - (double) protein * 4 - carbs * 4) / 9.0));
            }
        } else {
            protein = (int) Math.round(weight * proteinPerKg);
            carbs = (int) Math.round(weight * carbsPerKg);
            fat = (int) Math.round(weight * fatPerKg);
        }

        // 5. 餐次与带克数食谱（按 variant 轮换食物组合）
        List<Map<String, Object>> meals = buildMeals(goal, dailyCalories, variant);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("height", height);
        data.put("weight", weight);
        data.put("age", age);
        data.put("gender", gender);
        data.put("goal", goal);
        data.put("activityLevel", activityLevel);
        data.put("dailyCalories", dailyCalories);
        data.put("protein", protein);
        data.put("carbs", carbs);
        data.put("fat", fat);
        data.put("meals", meals);
        return data;
    }

    /**
     * 按目标调整后的默认每日热量（TDEE：增肌 +400 / 减脂 -500 / 保持 0，下限 1200）
     */
    public static int calcTargetCalories(double tdee, String goal) {
        int dailyCalories;
        switch (goal) {
            case "MUSCLE_GAIN": dailyCalories = (int) Math.round(tdee + 400); break;
            case "FAT_LOSS":    dailyCalories = (int) Math.round(tdee - 500); break;
            default:            dailyCalories = (int) Math.round(tdee);
        }
        return Math.max(dailyCalories, 1200);
    }

    /**
     * 指定热量边界钳制：低于 800 按 800（过低伤基础代谢），高于 5000 按 5000
     */
    public static int clampCalories(int calories) {
        return Math.max(800, Math.min(calories, 5000));
    }

    /**
     * 构建单个餐次
     */
    public static Map<String, Object> buildMeal(String type, List<String> foods, int calories) {
        Map<String, Object> meal = new LinkedHashMap<>();
        meal.put("type", type);
        meal.put("foods", foods);
        meal.put("calories", calories);
        return meal;
    }

    private static void validate(Integer height, Integer weight, Integer age,
                                 String gender, String goal) {
        if (height == null || height < 100 || height > 250) {
            throw new IllegalArgumentException("身高需在 100-250cm 之间");
        }
        if (weight == null || weight < 30 || weight > 300) {
            throw new IllegalArgumentException("体重需在 30-300kg 之间");
        }
        if (age == null || age < 10 || age > 100) {
            throw new IllegalArgumentException("年龄需在 10-100 岁之间");
        }
        if (gender == null || (!"MALE".equals(gender) && !"FEMALE".equals(gender))) {
            throw new IllegalArgumentException("性别必须为 MALE 或 FEMALE");
        }
        if (goal == null || (!"MUSCLE_GAIN".equals(goal) && !"FAT_LOSS".equals(goal) && !"MAINTAIN".equals(goal))) {
            throw new IllegalArgumentException("目标必须为 MUSCLE_GAIN / FAT_LOSS / MAINTAIN");
        }
    }

    /**
     * 食谱变体库：目标 → 餐次（早/午/晚/加餐）→ 3 套食物组合（同量级替换，克数固定）。
     * 同参数换 variant 即可换一份不同的食谱，营养结构保持一致（蛋白主食 + 主食 + 蔬果）。
     */
    private static final Map<String, List<List<List<String>>>> MEAL_VARIANTS = Map.of(
            "MUSCLE_GAIN", List.of(
                    // 早餐
                    List.of(
                            List.of("水煮蛋 2个(约100g)", "燕麦 60g", "牛奶 250ml", "香蕉 1根(约120g)"),
                            List.of("鸡蛋 2个(约100g)", "全麦面包 2片(约60g)", "牛奶 250ml", "蓝莓 80g"),
                            List.of("蒸蛋羹 1碗(约150g)", "燕麦 60g", "无糖酸奶 150g", "苹果 1个(约200g)")),
                    // 午餐
                    List.of(
                            List.of("鸡胸肉 200g", "糙米饭 150g", "西兰花 150g", "红薯 100g"),
                            List.of("牛腱肉 180g", "米饭 150g", "芦笋 150g", "玉米 1根(约200g)"),
                            List.of("龙利鱼 220g", "杂粮饭 150g", "菠菜 150g", "土豆 150g")),
                    // 晚餐
                    List.of(
                            List.of("三文鱼 150g", "杂粮饭 120g", "蔬菜沙拉 150g"),
                            List.of("瘦牛肉 150g", "意面 120g", "番茄炒蛋 1份"),
                            List.of("大虾 180g", "糙米饭 120g", "炒时蔬 200g")),
                    // 加餐
                    List.of(
                            List.of("蛋白粉 1勺(约30g)", "全麦面包 2片(约60g)", "混合坚果 25g"),
                            List.of("无糖酸奶 200g", "香蕉 1根(约120g)", "混合坚果 25g"),
                            List.of("蛋白粉 1勺(约30g)", "红薯 150g", "花生酱 15g"))),
            "FAT_LOSS", List.of(
                    // 早餐
                    List.of(
                            List.of("鸡蛋清 3个(约100g)", "燕麦 40g", "蓝莓 80g"),
                            List.of("水煮蛋 1个(约50g)", "无糖豆浆 300ml", "全麦面包 1片(约30g)", "圣女果 100g"),
                            List.of("蒸蛋羹 1碗(约150g)", "燕麦 40g", "黄瓜 1根(约200g)")),
                    // 午餐
                    List.of(
                            List.of("鸡胸肉 150g", "糙米饭 100g", "西兰花 150g", "番茄 1个(约150g)"),
                            List.of("龙利鱼 180g", "藜麦饭 100g", "芦笋 150g"),
                            List.of("虾仁 150g", "玉米 1根(约200g)", "凉拌菠菜 150g")),
                    // 晚餐
                    List.of(
                            List.of("清蒸鱼 150g", "蔬菜沙拉 200g", "黄瓜 1根(约200g)"),
                            List.of("鸡胸肉 120g", "冬瓜汤 1碗", "凉拌木耳 150g"),
                            List.of("豆腐 200g", "蔬菜沙拉 200g", "紫薯 100g")),
                    // 加餐
                    List.of(
                            List.of("无糖酸奶 150g", "苹果 1个(约200g)"),
                            List.of("水煮蛋 1个(约50g)", "圣女果 150g"),
                            List.of("无糖酸奶 150g", "蓝莓 80g", "巴旦木 10g"))),
            "MAINTAIN", List.of(
                    // 早餐
                    List.of(
                            List.of("水煮蛋 2个(约100g)", "全麦面包 2片(约60g)", "牛奶 250ml"),
                            List.of("鸡蛋 1个(约50g)", "小米粥 1碗", "蒸南瓜 150g"),
                            List.of("豆浆 300ml", "蔬菜鸡蛋饼 1份(约150g)", "苹果 1个(约200g)")),
                    // 午餐
                    List.of(
                            List.of("鸡胸肉 150g", "米饭 150g", "时蔬 200g", "番茄蛋汤 1碗"),
                            List.of("瘦猪里脊 130g", "米饭 150g", "炒青菜 200g", "紫菜汤 1碗"),
                            List.of("清蒸鱼 160g", "米饭 150g", "白灼菜心 200g")),
                    // 晚餐
                    List.of(
                            List.of("鱼肉 150g", "蒸土豆 150g", "蔬菜沙拉 150g"),
                            List.of("虾仁 150g", "杂粮饭 120g", "上汤娃娃菜 200g"),
                            List.of("卤牛肉 100g", "蒸山药 150g", "炒时蔬 200g")),
                    // 加餐
                    List.of(
                            List.of("应季水果 200g", "坚果 15g"),
                            List.of("无糖酸奶 150g", "香蕉 1根(约120g)"),
                            List.of("应季水果 150g", "水煮蛋 1个(约50g)"))));

    private static final List<String> MEAL_TYPES = List.of("早餐", "午餐", "晚餐", "加餐");
    private static final double[] MEAL_RATIOS = {0.25, 0.35, 0.25, 0.15};

    /**
     * 按目标 + 变体取食物库（全部带克数）+ 热量分配：早 25% / 午 35% / 晚 25% / 加餐 15%
     */
    private static List<Map<String, Object>> buildMeals(String goal, int dailyCalories, int variant) {
        List<List<List<String>>> byMeal = MEAL_VARIANTS.getOrDefault(goal, MEAL_VARIANTS.get("MAINTAIN"));
        int idx = Math.floorMod(variant, 3);
        List<Map<String, Object>> meals = new ArrayList<>();
        for (int i = 0; i < MEAL_TYPES.size(); i++) {
            int calories = (int) Math.round(dailyCalories * MEAL_RATIOS[i]);
            meals.add(buildMeal(MEAL_TYPES.get(i), byMeal.get(i).get(idx), calories));
        }
        return meals;
    }

}
