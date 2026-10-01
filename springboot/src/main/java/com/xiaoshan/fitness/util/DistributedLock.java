package com.xiaoshan.fitness.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式锁（轻量级实现）
 * <p>
 * 设计取舍：不引入 Redisson 等重型依赖，使用 RedisTemplate + SETNX + Lua 完成基础分布式锁。
 * 适用于短时间互斥（如激活/核销并发互斥），不保证可重入、不保证公平性、不保证锁续期。
 * <p>
 * 释放安全：value 为随机 UUID，释放时用 Lua 脚本比对 value，避免误删别人的锁
 * （典型场景：A 拿到锁 → TTL 到期自动释放 → B 拿到锁 → A 调用 unlock 误删 B 的锁）。
 * <p>
 * 使用建议：
 * <ul>
 *   <li>TTL 应略大于业务最坏执行时间（含 GC、网络抖动）；</li>
 *   <li>调用方应使用 try-finally 确保释放，或使用 {@link #executeWithLock} 自动管理；</li>
 *   <li>锁失败时抛 {@link BusinessException}(429, ...) 由全局异常处理器返回统一响应。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Lua 释放脚本：仅当 key 存在且 value 匹配时才删除（防止误删别人的锁） */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /**
     * 尝试获取分布式锁（非阻塞）。
     *
     * @param key 锁的 key（建议已包含业务前缀，如 "membership:activate:123"）
     * @param ttl 锁的过期时间（过期后自动释放，避免死锁）
     * @return 锁的唯一 token（释放时使用）；获取失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    /**
     * 释放锁（仅当 token 匹配时才删除，防止误删别人的锁）。
     * 释放失败不影响业务流程（TTL 会兜底自动过期）。
     */
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        try {
            Long result = redisTemplate.execute(
                    UNLOCK_SCRIPT,
                    List.of(key),
                    token
            );
            if (result == null || result == 0L) {
                // 锁已过期或被别人持有时尝试释放，属于正常现象（TTL 兜底）
                log.debug("分布式锁释放未命中（key={}, 可能已过期）", key);
            }
        } catch (Exception e) {
            log.warn("分布式锁释放异常：key={}", key, e);
        }
    }

    /**
     * 在锁内执行业务逻辑，自动管理获取与释放。
     * <p>
     * 获取锁失败时抛 {@link BusinessException}(429, "操作过于频繁，请稍后重试")，
     * 由 {@link GlobalExceptionHandler} 统一返回响应。
     *
     * @param key    锁 key
     * @param ttl    锁 TTL
     * @param action 业务逻辑
     * @param <T>    返回类型
     * @return 业务逻辑返回值
     */
    public <T> T executeWithLock(String key, Duration ttl, Supplier<T> action) {
        String token = tryLock(key, ttl);
        if (token == null) {
            throw new BusinessException(429, "操作过于频繁，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }
}
