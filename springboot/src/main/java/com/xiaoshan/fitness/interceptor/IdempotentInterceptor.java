package com.xiaoshan.fitness.interceptor;

import com.xiaoshan.fitness.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 幂等拦截器
 * <p>
 * 匹配带 {@link Idempotent} 注解的 Controller 方法，在 preHandle 阶段执行
 * Redis SETNX 进行窗口期幂等控制。窗口期内重复请求被拒绝（HTTP 429）。
 * <p>
 * 未登录用户（principal 为空或 anonymousUser）跳过幂等检查，避免无意义拦截。
 * 静态资源 / 非 HandlerMethod 请求直接放行。
 * <p>
 * 注意：不做请求体哈希，仅以 userId + method + uri 为 key。
 * 这意味着同一用户对同一接口在窗口期内只能调用一次，与参数无关。
 * 业务幂等（如同一订单重复支付）由 DB 约束兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";
    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        Idempotent idempotent = hm.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }

        String userId = resolveCaller();
        // 未登录用户不参与幂等拦截（登录接口本身不需要幂等）
        if (userId == null || ANONYMOUS_PRINCIPAL.equals(userId)) {
            return true;
        }

        String uri = request.getRequestURI();
        String httpMethod = request.getMethod();
        String key = IDEMPOTENT_KEY_PREFIX + userId + ":" + httpMethod + ":" + uri;
        Duration ttl = Duration.ofSeconds(idempotent.expireSeconds());

        // SETNX + TTL：原子操作，成功返回 true，已存在返回 false
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        if (Boolean.FALSE.equals(acquired)) {
            // 幂等拦截：窗口期内重复请求
            log.warn("幂等拦截: userId={}, method={}, uri={}, ttl={}s",
                    userId, httpMethod, uri, idempotent.expireSeconds());
            writeBlockResponse(response);
            return false;
        }
        return true;
    }

    /**
     * 写入 429 拦截响应（统一 JSON 格式，前端拦截器可识别 code=429）
     */
    private void writeBlockResponse(HttpServletResponse response) {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write("{\"success\":false,\"message\":\"操作过于频繁，请稍后重试\",\"code\":429}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("写入幂等拦截响应失败", e);
        }
    }

    /**
     * 从 SecurityContext 解析调用者 ID（与 JwtAuthenticationFilter 中 principal 一致）
     */
    private String resolveCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        String name = principal.toString();
        return ANONYMOUS_PRINCIPAL.equals(name) ? null : name;
    }
}
