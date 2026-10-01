package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.service.AiCustomerService;
import com.xiaoshan.fitness.service.TrainingPlanService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 助手接口（训练计划 + 智能客服）。
 * <ul>
 *   <li>POST /api/v1/user/ai-assistant/training/generate 生成结构化一周训练计划</li>
 *   <li>POST /api/v1/user/ai-assistant/customer/chat     智能客服问答（FAQ→千帆→人工）</li>
 *   <li>GET  /api/v1/user/ai-assistant/customer/suggested 客服推荐问题</li>
 * </ul>
 * 均需普通用户登录。计算/生成失败不向用户暴露堆栈，由各 Service 内部兜底。
 */
@RestController
@RequestMapping("/api/v1/user/ai-assistant")
@RequiredArgsConstructor
@Slf4j
public class AiAssistantController {

    private final JwtUtil jwtUtil;
    private final TrainingPlanService trainingPlanService;
    private final AiCustomerService aiCustomerService;

    /**
     * 生成训练计划
     * body: { goal, level, equipment, daysPerWeek }
     */
    @PostMapping("/training/generate")
    public Result<Map<String, Object>> training(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String goal = str(body.get("goal"));
        String level = str(body.get("level"));
        String equipment = str(body.get("equipment"));
        Integer daysPerWeek = intVal(body.get("daysPerWeek"));

        try {
            Map<String, Object> data = trainingPlanService.generate(goal, level, equipment, daysPerWeek);
            return Result.ok(data);
        } catch (Exception e) {
            log.error("AI 训练计划生成异常", e);
            return Result.fail("训练计划生成失败，请稍后重试");
        }
    }

    /**
     * 智能客服问答
     * body: { message }
     */
    @PostMapping("/customer/chat")
    public Result<Map<String, Object>> customerChat(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String message = str(body.get("message"));
        if (message.isEmpty()) {
            return Result.fail("问题内容不能为空");
        }
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        Map<String, Object> data = aiCustomerService.answer(message);
        return Result.ok(data);
    }

    /** 客服推荐问题（首屏引导） */
    @GetMapping("/customer/suggested")
    public Result<Map<String, Object>> suggested(HttpServletRequest request) {
        if (currentUserId(request) == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", aiCustomerService.suggestedQuestions());
        return Result.ok(data);
    }

    // ==================== 工具 ====================

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

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private Integer intVal(Object o) {
        if (o == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
