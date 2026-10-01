package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.CustomerServiceMessage;
import com.xiaoshan.fitness.mapper.CustomerServiceMessageMapper;
import com.xiaoshan.fitness.service.OnlineStatusService;
import com.xiaoshan.fitness.service.ServiceChatService;
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
 * 客服通信 REST 接口（历史消息 / 会话列表 / 在线状态）
 * 按 Token 类型分流：user 查自己会话，admin 管理会话列表
 */
@RestController
@RequestMapping("/api/v1/service")
@RequiredArgsConstructor
@Slf4j
public class ServiceController {

    private final JwtUtil jwtUtil;
    private final ServiceChatService chatService;
    private final OnlineStatusService onlineStatusService;
    private final CustomerServiceMessageMapper messageMapper;

    /**
     * 标记自己在线（进入客服页面调用）
     */
    @PostMapping("/online")
    public Result<String> online(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        if (id.isAdmin()) {
            onlineStatusService.markAdminOnline(id.id());
        } else {
            onlineStatusService.markUserOnline(id.id());
        }
        return Result.ok("已上线");
    }

    /**
     * 标记自己离线（离开页面调用）
     */
    @PostMapping("/offline")
    public Result<String> offline(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        if (id.isAdmin()) {
            onlineStatusService.removeAdminOnline(id.id());
        } else {
            onlineStatusService.removeUserOnline(id.id());
        }
        return Result.ok("已下线");
    }

    /**
     * 心跳续期（页面停留期间定时调用，保持 30 分钟 TTL）
     */
    @PostMapping("/heartbeat")
    public Result<String> heartbeat(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        if (id.isAdmin()) {
            onlineStatusService.markAdminOnline(id.id());
        } else {
            onlineStatusService.markUserOnline(id.id());
        }
        return Result.ok("ok");
    }

    /**
     * 在线状态查询
     * - 用户：返回客服是否在线
     * - 客服：返回自己是否在线
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (id.isAdmin()) {
            data.put("online", onlineStatusService.isAnyAdminOnline());
        } else {
            data.put("adminOnline", onlineStatusService.isAnyAdminOnline());
        }
        return Result.ok(data);
    }

    /**
     * 用户未读客服消息数（消息中心 Tab 角标）
     */
    @GetMapping("/unread")
    public Result<Map<String, Object>> unread(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null || id.isAdmin()) {
            return Result.fail("仅用户可查询");
        }
        long count = messageMapper.countUnreadForUser(id.id());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }

    /**
     * 管理员未读消息总数（所有用户发来的未读消息汇总，用于角标展示）
     */
    @GetMapping("/admin/unread-count")
    public Result<Map<String, Object>> adminUnreadCount(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null || !id.isAdmin()) {
            return Result.fail("仅客服可查询");
        }
        long count = messageMapper.countUnreadForAdminAll();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }

    /**
     * 会话列表（客服端）：每个用户最后一条消息 + 未读数 + 在线状态
     */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null || !id.isAdmin()) {
            return Result.fail("仅客服可访问");
        }
        List<Map<String, Object>> list = messageMapper.findConversations().stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", row.get("userId"));
                    item.put("phone", row.get("phone"));
                    item.put("avatar", row.get("avatar"));
                    item.put("lastContent", row.get("lastContent"));
                    item.put("lastSenderType", row.get("lastSenderType"));
                    item.put("lastTime", row.get("lastTime") != null
                            ? row.get("lastTime").toString().replace('T', ' ')
                                    .substring(0, Math.min(19, row.get("lastTime").toString().length()))
                            : null);
                    item.put("unread", row.get("unread"));
                    item.put("online", onlineStatusService.isUserOnline((Long) row.get("userId")));
                    return item;
                })
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 历史消息（按时间正序返回）
     * - 用户：查自己
     * - 客服：必须传 userId
     */
    @GetMapping("/messages")
    public Result<Map<String, Object>> messages(HttpServletRequest request,
                                                @RequestParam(required = false) Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        Long targetUserId;
        if (id.isAdmin()) {
            if (userId == null) {
                return Result.fail("缺少 userId 参数");
            }
            targetUserId = userId;
        } else {
            targetUserId = id.id();
        }

        int p = Math.max(1, page);
        int offset = (p - 1) * Math.max(1, Math.min(size, 100));
        List<CustomerServiceMessage> rows =
                messageMapper.findPageByUserId(targetUserId, offset, size);
        long total = messageMapper.countByUserId(targetUserId);

        // 倒序查询转正序
        List<Map<String, Object>> list = new java.util.ArrayList<>(rows).stream()
                .sorted(java.util.Comparator.comparing(CustomerServiceMessage::getCreatedAt)
                        .thenComparing(CustomerServiceMessage::getId))
                .map(m -> chatService.buildVO(m, null,
                        CustomerServiceMessage.SENDER_USER.equals(m.getSenderType())
                                ? m.getUserId() : null))
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", p);
        return Result.ok(data);
    }

    /**
     * 用户标记客服消息全部已读
     */
    @PostMapping("/read")
    public Result<String> markRead(HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null || id.isAdmin()) {
            return Result.fail("仅用户可操作");
        }
        messageMapper.markReadForUser(id.id());
        return Result.ok("已读");
    }

    /**
     * 客服标记某用户消息已读
     */
    @PostMapping("/{userId}/read")
    public Result<String> markUserRead(@PathVariable Long userId, HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null || !id.isAdmin()) {
            return Result.fail("仅客服可操作");
        }
        messageMapper.markReadForAdmin(userId);
        return Result.ok("已读");
    }

    /**
     * REST 发送消息（WebSocket 不可用时的兜底通道）
     * - 用户：直接发送
     * - 客服：必须传 toUserId
     */
    @PostMapping("/send")
    public Result<Map<String, Object>> send(@RequestBody Map<String, Object> payload,
                                            HttpServletRequest request) {
        Identity id = identity(request);
        if (id == null) {
            return Result.fail("未登录或登录已过期");
        }
        String content = (String) payload.get("content");
        try {
            if (id.isAdmin()) {
                Object to = payload.get("toUserId");
                if (to == null) {
                    return Result.fail("缺少 toUserId");
                }
                return Result.ok(chatService.sendAdminMessage(id.id(), Long.parseLong(to.toString()), content));
            }
            return Result.ok(chatService.sendUserMessage(id.id(), content));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 当前身份（id + 是否客服）
     */
    private Identity identity(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        String type = jwtUtil.getTypeFromToken(token);
        if (type == null) {
            return null;
        }
        boolean admin = JwtUtil.TYPE_ADMIN.equals(type);
        return new Identity(jwtUtil.getUserIdFromToken(token), admin);
    }

    private record Identity(Long id, boolean isAdmin) {
    }

}
