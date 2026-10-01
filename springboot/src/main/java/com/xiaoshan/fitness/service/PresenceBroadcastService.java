package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客服在线状态实时广播服务
 * <p>
 * Redis 负责在线状态的持久化（页面刷新后仍以 Redis 为准），本服务只负责「状态变化」的实时推送：
 * <ul>
 *   <li>管理员上下线 → 广播给所有在线用户，用户端订阅 /user/queue/service-status
 *       （STOMP 目的地 /queue/service-status，principal = user:{userId}）</li>
 *   <li>用户上下线 → 广播给所有在线管理员，管理员端订阅 /user/queue/service-user-status
 *       （STOMP 目的地 /queue/service-user-status，principal = admin:{adminId}）</li>
 * </ul>
 * 载荷统一为 JSON 字符串。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final OnlineStatusService onlineStatusService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 用户端订阅：客服在线状态变化目的地 */
    public static final String USER_DEST_SERVICE_STATUS = "/queue/service-status";
    /** 管理员端订阅：用户在线状态变化目的地 */
    public static final String ADMIN_DEST_USER_STATUS = "/queue/service-user-status";

    /**
     * 管理员上线：广播给所有在线用户
     */
    public void broadcastAdminOnline() {
        String payload = toJson(Map.of("type", "ADMIN_ONLINE"));
        for (String userPrincipal : onlineStatusService.connectedUserPrincipals()) {
            messagingTemplate.convertAndSendToUser(userPrincipal, USER_DEST_SERVICE_STATUS, payload);
        }
        log.info("广播管理员上线 → {} 个在线用户", onlineStatusService.connectedUserPrincipals().size());
    }

    /**
     * 管理员下线：广播给所有在线用户
     */
    public void broadcastAdminOffline() {
        String payload = toJson(Map.of("type", "ADMIN_OFFLINE"));
        for (String userPrincipal : onlineStatusService.connectedUserPrincipals()) {
            messagingTemplate.convertAndSendToUser(userPrincipal, USER_DEST_SERVICE_STATUS, payload);
        }
        log.info("广播管理员下线 → {} 个在线用户", onlineStatusService.connectedUserPrincipals().size());
    }

    /**
     * 用户上线：广播给所有在线管理员
     */
    public void broadcastUserOnline(Long userId) {
        String payload = toJson(buildUserStatus("USER_ONLINE", userId));
        for (String adminPrincipal : onlineStatusService.connectedAdminPrincipals()) {
            messagingTemplate.convertAndSendToUser(adminPrincipal, ADMIN_DEST_USER_STATUS, payload);
        }
        log.info("广播用户{}上线 → {} 个在线管理员", userId, onlineStatusService.connectedAdminPrincipals().size());
    }

    /**
     * 用户下线：广播给所有在线管理员
     */
    public void broadcastUserOffline(Long userId) {
        String payload = toJson(buildUserStatus("USER_OFFLINE", userId));
        for (String adminPrincipal : onlineStatusService.connectedAdminPrincipals()) {
            messagingTemplate.convertAndSendToUser(adminPrincipal, ADMIN_DEST_USER_STATUS, payload);
        }
        log.info("广播用户{}下线 → {} 个在线管理员", userId, onlineStatusService.connectedAdminPrincipals().size());
    }

    private Map<String, Object> buildUserStatus(String type, Long userId) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("type", type);
        vo.put("userId", userId);
        return vo;
    }

    private String toJson(Map<String, Object> vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            log.error("在线状态广播序列化失败", e);
            return "{}";
        }
    }
}
