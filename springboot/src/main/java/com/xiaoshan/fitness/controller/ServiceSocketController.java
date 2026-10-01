package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.service.ServiceChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * 客服聊天 WebSocket 控制器
 * 客户端发送目的地：/app/service.send，载荷：{"content": "...", "toUserId": 123(客服必填)}
 * 身份由握手阶段映射：Principal.name = user:{userId} / admin:{adminId}
 * 载荷统一为 JSON 字符串，前端 JSON.parse 解析
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ServiceSocketController {

    private final ServiceChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    /**
     * 发送客服消息
     */
    @MessageMapping("/service.send")
    @SendToUser("/queue/service.error")
    public String send(@Payload String body, Principal principal) {
        if (principal == null) {
            return error("未认证连接");
        }
        String name = principal.getName();
        try {
            Map<?, ?> payload = objectMapper.readValue(body, Map.class);
            String content = (String) payload.get("content");

            if (name.startsWith("user:")) {
                Long userId = Long.parseLong(name.substring(5));
                chatService.sendUserMessage(userId, content);
            } else if (name.startsWith("admin:")) {
                Long adminId = Long.parseLong(name.substring(6));
                Object to = payload.get("toUserId");
                if (to == null) {
                    return error("缺少 toUserId");
                }
                chatService.sendAdminMessage(adminId, Long.parseLong(to.toString()), content);
            } else {
                return error("未知身份");
            }
            return null; // 正常时无需错误回执（消息本体已通过 /queue/service 推送）
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("处理客服消息失败，principal={}", name, e);
            return error("消息发送失败");
        }
    }

    private String error(String msg) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("error", msg));
        } catch (Exception e) {
            return "{\"error\":\"" + msg + "\"}";
        }
    }

}
