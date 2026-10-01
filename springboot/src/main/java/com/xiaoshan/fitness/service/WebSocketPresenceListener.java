package com.xiaoshan.fitness.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket 在线状态监听
 * <p>
 * 以 WebSocket 连接生命周期为权威数据源，统一维护 Redis 在线标记 + 实时广播：
 * <ul>
 *   <li>连接建立（首条连接，0→1）：写 Redis + 广播上线</li>
 *   <li>连接断开（最后一条断开，1→0）：删 Redis + 广播下线</li>
 *   <li>多标签页：仅在首条/末条连接时触发，中间标签开关不产生上下线抖动</li>
 * </ul>
 * Redis 30 分钟 TTL 仅作兜底（服务重启/事件丢失），定时任务 {@link #refreshTtl()} 周期续期。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketPresenceListener {

    private final OnlineStatusService onlineStatusService;
    private final PresenceBroadcastService presenceBroadcastService;

    /**
     * 连接建立：首条连接时写 Redis 并广播上线
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        String principalName = user.getName();
        boolean first = onlineStatusService.onWsConnected(principalName);
        if (!first) {
            // 已有连接（多标签页），不重复上线
            return;
        }
        if (principalName.startsWith("user:")) {
            Long userId = Long.parseLong(principalName.substring(5));
            onlineStatusService.markUserOnline(userId);
            presenceBroadcastService.broadcastUserOnline(userId);
            log.info("用户上线：{}", principalName);
        } else if (principalName.startsWith("admin:")) {
            Long adminId = Long.parseLong(principalName.substring(6));
            onlineStatusService.markAdminOnline(adminId);
            presenceBroadcastService.broadcastAdminOnline();
            log.info("客服上线：{}", principalName);
        }
    }

    /**
     * 连接断开：最后一条连接断开时删 Redis 并广播下线
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        String principalName = user.getName();
        boolean zero = onlineStatusService.onWsDisconnected(principalName);
        if (!zero) {
            // 仍有其他连接（多标签页），保持在线
            return;
        }
        // 该身份的所有连接均已断开 → 删除在线标记并广播下线
        if (principalName.startsWith("user:")) {
            Long userId = Long.parseLong(principalName.substring(5));
            onlineStatusService.removeUserOnline(userId);
            presenceBroadcastService.broadcastUserOffline(userId);
            log.info("用户离线：{}", principalName);
        } else if (principalName.startsWith("admin:")) {
            Long adminId = Long.parseLong(principalName.substring(6));
            onlineStatusService.removeAdminOnline(adminId);
            presenceBroadcastService.broadcastAdminOffline();
            log.info("客服离线：{}", principalName);
        }
    }

    /**
     * 每 5 分钟为所有活跃 WebSocket 连接续期 Redis 在线标记，
     * 防止长连接期间 30 分钟 TTL 自然过期导致「连接在线但 Redis 显示离线」。
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void refreshTtl() {
        onlineStatusService.refreshAllTtl();
    }

}
