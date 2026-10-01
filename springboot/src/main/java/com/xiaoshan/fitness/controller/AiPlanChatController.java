package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.service.AiChatService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AI 对话式饮食计划接口
 * - POST /api/v1/user/ai-plan/chat 对话（入参 sessionId + message）
 * <p>
 * 上下文由后端按 sessionId 存 Redis（30 分钟滑动过期）；
 * planData 由后端 NutritionCalculator 精确计算，不经过大模型。
 */
@RestController
@RequestMapping("/api/v1/user/ai-plan")
@RequiredArgsConstructor
@Slf4j
public class AiPlanChatController {

    private final JwtUtil jwtUtil;
    private final AiChatService aiChatService;

    /**
     * AI 对话
     * 入参：{sessionId?, message}
     * 出参：{sessionId, reply, status: ASKING|PLAN_READY, step?, planData?}
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        String sessionId = body.get("sessionId") == null ? "" : String.valueOf(body.get("sessionId")).trim();
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message")).trim();

        if (message.isEmpty()) {
            return Result.fail("消息不能为空");
        }
        // 消息长度限制，防止滥用
        if (message.length() > 200) {
            message = message.substring(0, 200);
        }
        // 前端未携带 sessionId 时由后端生成
        if (sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        try {
            Map<String, Object> data = aiChatService.sendMessage(userId, sessionId, message);
            return Result.ok(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("AI 对话异常", e);
            return Result.fail("AI 开小差了，请稍后重试");
        }
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

}
