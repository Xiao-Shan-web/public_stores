package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户消息通知接口
 * 类型：VERIFY 核销成功 / EXPIRE 到期提醒 / SYSTEM 系统公告 / ACTIVITY 活动 /
 *       INTERACT 分享互动 / AI_PLAN AI计划 / COMPLAINT 投诉进度
 * <p>
 * 类型取值与前端展示强耦合，详见 {@link com.xiaoshan.fitness.entity.Message#getType()}。
 */
@RestController
@RequestMapping("/api/v1/message")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final JwtUtil jwtUtil;
    private final MessageMapper messageMapper;

    /**
     * 消息列表（按时间倒序）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        int offset = Math.max(0, (page - 1) * size);
        List<Message> messages = messageMapper.findPageByUserId(userId, offset, size);
        long total = messageMapper.countByUserId(userId);

        List<Map<String, Object>> list = messages.stream()
                .map(this::buildMessageVO)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 未读消息数（用于底部导航 Badge 展示）
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        long count = messageMapper.countUnread(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }

    /**
     * 标记单条消息已读
     */
    @PostMapping("/{id}/read")
    public Result<String> markRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        messageMapper.markRead(id, userId);
        return Result.ok("已标记为已读");
    }

    /**
     * 标记全部已读
     */
    @PostMapping("/read-all")
    public Result<String> markAllRead(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        int rows = messageMapper.markAllRead(userId);
        log.info("用户{}标记{}条消息为已读", userId, rows);
        return Result.ok("已全部标记为已读");
    }

    /**
     * 从请求解析当前用户ID
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
     * 构建消息视图对象
     */
    private Map<String, Object> buildMessageVO(Message m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", m.getId());
        item.put("type", m.getType());
        item.put("title", m.getTitle());
        item.put("content", m.getContent());
        item.put("refId", m.getRefId());
        // TINYINT(1) 统一以 0/1 整型下发，前端用 Number(isRead) === 1 判断，避免布尔误判
        item.put("isRead", m.getIsRead() == null ? 0 : m.getIsRead());
        item.put("createdAt", m.getCreatedAt());
        return item;
    }

}
