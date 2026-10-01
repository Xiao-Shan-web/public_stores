package com.xiaoshan.fitness.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 通用缓存服务（写穿透模式）
 * <p>
 * 流程：先查 Redis → 命中则按具体类型反序列化返回 → 未命中查 DB 并回写 Redis → 返回。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>不使用 @Cacheable（需配置 CacheManager，且与 RedisTemplate 序列化方案对接复杂）；</li>
 *   <li><b>缓存值统一用 {@link StringRedisTemplate} 存纯 JSON 字符串</b>，序列化/反序列化都走
 *       Spring 容器中配置了时间格式的 {@link ObjectMapper}，不带任何 Jackson 默认类型信息（@class）。
 *       因此缓存对象必须是具体类型（如 {@code List<CardTypeVO>}、具体 VO），
 *       <b>禁止缓存 {@code Map<String, Object>}</b>——GenericJackson2JsonRedisSerializer
 *       对 Object 根值会抛 "Type id handling not implemented for type java.lang.Object"；</li>
 *   <li>读取时由调用方提供 {@link TypeReference} 或 {@link Class} 还原具体类型，
 *       避免泛型擦除导致 ClassCastException / LinkedHashMap 漂移；</li>
 *   <li>loader 返回 null 时不缓存（防止缓存穿透的简单策略，未做布隆/空值占位）；</li>
 *   <li>异常降级：Redis 不可用或旧格式数据反序列化失败时回退到直接查 DB，保证业务可用。</li>
 * </ul>
 * <p>
 * 使用建议：仅缓存读多写少、变更频率低、可容忍短暂不一致的数据（如卡类型列表、门店列表、管理员信息）。
 * 写操作（增删改）必须显式调用 {@link #evict(String)} 失效对应缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写穿透缓存（泛型复杂类型版本，如 {@code List<CardTypeVO>}）。
     *
     * @param key    缓存 key（建议包含业务前缀，如 "cardtype:list:active"）
     * @param ttl    缓存有效期
     * @param loader DB 加载函数（缓存未命中时调用），返回值必须是具体类型，禁止 Map&lt;String, Object&gt;
     * @param typeRef 返回值类型引用（解决泛型擦除）
     * @return 缓存或 DB 中的值
     */
    public <T> T cacheThrough(String key, Duration ttl, Supplier<T> loader, TypeReference<T> typeRef) {
        // 1. 先查 Redis
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, typeRef);
            }
        } catch (Exception e) {
            // Redis 不可用或旧格式数据反序列化失败：降级为直接查 DB（DB 回填后自然覆盖旧值）
            log.warn("缓存读取异常，降级查 DB：key={}", key, e);
        }

        // 2. 未命中或异常，查 DB
        T value = loader.get();
        if (value == null) {
            return null;
        }

        // 3. 回写 Redis（纯 JSON 字符串，失败不影响业务）
        writeJson(key, value, ttl);

        return value;
    }

    /**
     * 写穿透缓存（简单具体类型版本，如单个 VO）。
     */
    public <T> T cacheThrough(String key, Duration ttl, Supplier<T> loader, Class<T> clazz) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, clazz);
            }
        } catch (Exception e) {
            log.warn("缓存读取异常，降级查 DB：key={}", key, e);
        }

        T value = loader.get();
        if (value == null) {
            return null;
        }

        writeJson(key, value, ttl);
        return value;
    }

    /** 把具体类型对象序列化为纯 JSON 字符串写入缓存 */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("缓存回写失败：key={}", key, e);
        }
    }

    /**
     * 失效指定缓存。
     */
    public void evict(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("缓存失效异常：key={}", key, e);
        }
    }

    /**
     * 批量失效（按 key 前缀扫描删除，慎用：KEYS 会阻塞 Redis，仅用于低频写操作）。
     * 推荐场景：管理员编辑卡类型/门店后批量清理该业务的所有缓存变体。
     */
    public void evictByPrefix(String prefix) {
        try {
            java.util.Set<String> keys = stringRedisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("前缀缓存失效异常：prefix={}", prefix, e);
        }
    }
}
