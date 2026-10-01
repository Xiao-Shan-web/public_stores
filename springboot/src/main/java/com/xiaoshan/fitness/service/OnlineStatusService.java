package com.xiaoshan.fitness.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客服通信在线状态服务
 * <p>
 * 1. Redis 在线标记（30 分钟自动过期兜底）：
 *    - service:online:user:{userId} = 1
 *    - service:online:admin:{adminId} = 1
 * 2. WebSocket 连接计数（内存）：支持多标签页，最后一个连接断开才算离线
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineStatusService {

    public static final String KEY_USER_PREFIX = "service:online:user:";
    public static final String KEY_ADMIN_PREFIX = "service:online:admin:";

    /** 在线标记过期时间：30 分钟（心跳续期，离开/断线立即删除） */
    public static final Duration ONLINE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * WebSocket 连接计数：principalName -> 当前连接数
     */
    private final ConcurrentHashMap<String, AtomicInteger> wsSessionCounts = new ConcurrentHashMap<>();

    // ==================== Redis 在线标记 ====================

    /**
     * 标记用户在线（写入/续期，30 分钟过期）
     */
    public void markUserOnline(Long userId) {
        stringRedisTemplate.opsForValue().set(KEY_USER_PREFIX + userId, "1", ONLINE_TTL);
    }

    /**
     * 标记客服在线（写入/续期，30 分钟过期）
     */
    public void markAdminOnline(Long adminId) {
        stringRedisTemplate.opsForValue().set(KEY_ADMIN_PREFIX + adminId, "1", ONLINE_TTL);
    }

    /**
     * 删除用户在线标记
     */
    public void removeUserOnline(Long userId) {
        stringRedisTemplate.delete(KEY_USER_PREFIX + userId);
    }

    /**
     * 删除客服在线标记
     */
    public void removeAdminOnline(Long adminId) {
        stringRedisTemplate.delete(KEY_ADMIN_PREFIX + adminId);
    }

    /**
     * 用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_USER_PREFIX + userId));
    }

    /**
     * 是否有任一客服在线（用户端「客服在线/离线」展示）
     */
    public boolean isAnyAdminOnline() {
        Set<String> keys = stringRedisTemplate.keys(KEY_ADMIN_PREFIX + "*");
        return keys != null && !keys.isEmpty();
    }

    // ==================== WebSocket 连接计数 ====================

    /**
     * WebSocket 连接建立：计数 +1（principalName 形如 user:123 / admin:1）
     *
     * @return true 表示这是该身份的第一条连接（0→1），此时应写入 Redis 在线标记并广播上线；
     *         false 表示已有连接（多标签页），无需重复处理
     */
    public boolean onWsConnected(String principalName) {
        AtomicInteger counter = wsSessionCounts.computeIfAbsent(principalName, k -> new AtomicInteger(0));
        int now = counter.incrementAndGet();
        log.debug("WS连接建立：{}，当前计数={}", principalName, now);
        return now == 1;
    }

    /**
     * WebSocket 连接断开：计数 -1
     *
     * @return 计数归零返回 true（此时应删除在线标记）
     */
    public boolean onWsDisconnected(String principalName) {
        AtomicInteger count = wsSessionCounts.get(principalName);
        if (count == null) {
            return true;
        }
        int left = count.decrementAndGet();
        if (left <= 0) {
            wsSessionCounts.remove(principalName);
            return true;
        }
        return false;
    }

    /**
     * 当前在线的客服 principal 列表（形如 admin:1），用于把用户消息扇出到所有客服
     */
    public List<String> connectedAdminPrincipals() {
        List<String> result = new ArrayList<>();
        wsSessionCounts.keySet().forEach(name -> {
            if (name.startsWith("admin:")) {
                result.add(name);
            }
        });
        return result;
    }

    /**
     * 当前在线的用户 principal 列表（形如 user:123），用于客服上下线时广播给所有在线用户
     */
    public List<String> connectedUserPrincipals() {
        List<String> result = new ArrayList<>();
        wsSessionCounts.keySet().forEach(name -> {
            if (name.startsWith("user:")) {
                result.add(name);
            }
        });
        return result;
    }

    /**
     * 为所有持有 WebSocket 连接的身份续期 Redis 在线标记（定时任务调用）。
     * <p>
     * 长连接保持期间 Redis key 30 分钟 TTL 会自然过期，这里周期性重写，
     * 保证「WS 在线」与「Redis 在线」一致；断开的连接已不在 wsSessionCounts 中，不会被续期。
     */
    public void refreshAllTtl() {
        wsSessionCounts.keySet().forEach(name -> {
            try {
                if (name.startsWith("user:")) {
                    markUserOnline(Long.parseLong(name.substring(5)));
                } else if (name.startsWith("admin:")) {
                    markAdminOnline(Long.parseLong(name.substring(6)));
                }
            } catch (Exception e) {
                log.warn("在线状态续期失败：{}", name, e);
            }
        });
    }

}
