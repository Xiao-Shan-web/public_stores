package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.AiPlan;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.AiPlanMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.service.AiDietService;
import com.xiaoshan.fitness.service.NotificationPushService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 饮食计划接口
 * - POST /api/v1/ai/plan/generate 生成计划（不落库，仅返回结果）
 * - POST /api/v1/ai/plan/save    保存计划
 * - GET  /api/v1/ai/plan/list     历史计划列表
 * - GET  /api/v1/ai/plan/{id}     计划详情
 */
@RestController
@RequestMapping("/api/v1/ai/plan")
@RequiredArgsConstructor
@Slf4j
public class AiPlanController {

    private final JwtUtil jwtUtil;
    private final AiDietService aiDietService;
    private final AiPlanMapper aiPlanMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;
    /** 本地实例化，避免上下文未注册 ObjectMapper Bean */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成饮食计划（不落库）
     * 入参：{height, weight, age, gender, goal}
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        try {
            Integer height = toInt(body.get("height"));
            Integer weight = toInt(body.get("weight"));
            Integer age = toInt(body.get("age"));
            String gender = (String) body.get("gender");
            String goal = (String) body.get("goal");

            Map<String, Object> data = aiDietService.generatePlan(height, weight, age, gender, goal);
            return Result.ok(data, "计划生成成功");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("AI 计划生成异常", e);
            return Result.fail("计划生成失败，请稍后重试");
        }
    }

    /**
     * 保存生成的计划（用户在页面点击"保存"时调用）
     * 入参：{height, weight, age, gender, goal, dailyCalories, protein, carbs, fat, meals}
     */
    @PostMapping("/save")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        try {
            AiPlan plan = new AiPlan();
            plan.setId(idGenerator.nextId());
            plan.setUserId(userId);
            plan.setHeight(toInt(body.get("height")));
            plan.setWeight(toInt(body.get("weight")));
            plan.setAge(toInt(body.get("age")));
            plan.setGender((String) body.get("gender"));
            plan.setGoal((String) body.get("goal"));
            plan.setDailyCalories(toInt(body.get("dailyCalories")));
            plan.setProtein(toInt(body.get("protein")));
            plan.setCarbs(toInt(body.get("carbs")));
            plan.setFat(toInt(body.get("fat")));
            plan.setMealsJson(objectMapper.writeValueAsString(body.get("meals")));
            plan.setProvider(aiDietService.provider());
            aiPlanMapper.insert(plan);

            // 异步通知：计划生成完成（实际同步插入一条消息）
            Message msg = new Message();
            msg.setId(idGenerator.nextId());
            msg.setUserId(userId);
            msg.setType("AI_PLAN");
            msg.setTitle("AI 计划生成完成");
            msg.setContent("您的新饮食计划已保存，每日目标 " + plan.getDailyCalories() + " kcal");
            msg.setRefId(plan.getId());
            messageMapper.insert(msg);
            notificationPushService.push(msg);

            log.info("用户{}保存 AI 计划：每日{}kcal（ID={}）", userId, plan.getDailyCalories(), plan.getId());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", plan.getId());
            data.put("dailyCalories", plan.getDailyCalories());
            data.put("createdAt", plan.getCreatedAt());
            return Result.ok(data, "计划已保存");
        } catch (Exception e) {
            log.error("AI 计划保存异常", e);
            return Result.fail("保存失败");
        }
    }

    /**
     * 历史计划列表（按时间倒序）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        int offset = Math.max(0, (page - 1) * size);
        List<AiPlan> plans = aiPlanMapper.findPageByUserId(userId, offset, size);
        long total = aiPlanMapper.countByUserId(userId);

        List<Map<String, Object>> list = plans.stream()
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", p.getId());
                    item.put("height", p.getHeight());
                    item.put("weight", p.getWeight());
                    item.put("age", p.getAge());
                    item.put("gender", p.getGender());
                    item.put("goal", p.getGoal());
                    item.put("dailyCalories", p.getDailyCalories());
                    item.put("protein", p.getProtein());
                    item.put("carbs", p.getCarbs());
                    item.put("fat", p.getFat());
                    item.put("createdAt", p.getCreatedAt());
                    item.put("provider", p.getProvider());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 计划详情（含完整 meals）
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        AiPlan plan = aiPlanMapper.findByIdAndUserId(id, userId);
        if (plan == null) {
            return Result.fail("计划不存在");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", plan.getId());
        data.put("height", plan.getHeight());
        data.put("weight", plan.getWeight());
        data.put("age", plan.getAge());
        data.put("gender", plan.getGender());
        data.put("goal", plan.getGoal());
        data.put("dailyCalories", plan.getDailyCalories());
        data.put("protein", plan.getProtein());
        data.put("carbs", plan.getCarbs());
        data.put("fat", plan.getFat());
        data.put("provider", plan.getProvider());
        data.put("createdAt", plan.getCreatedAt());
        try {
            data.put("meals", objectMapper.readValue(plan.getMealsJson(), List.class));
        } catch (Exception e) {
            data.put("meals", List.of());
        }
        return Result.ok(data);
    }

    /**
     * 解析当前用户ID（仅接受普通用户 token）
     */
    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * 安全转 Integer
     */
    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
