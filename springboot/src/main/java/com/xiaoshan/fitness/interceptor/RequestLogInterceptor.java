package com.xiaoshan.fitness.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 请求日志拦截器
 * <p>
 * 拦截所有 Controller 请求，记录：
 * - HTTP method、URI、状态码、耗时、调用者 ID
 * - 关键链路（登录/支付/激活/核销）→ INFO 级别
 * - 慢接口（耗时 ≥ SLOW_THRESHOLD_MS）→ WARN 级别
 * - 服务端错误（status ≥ 500）→ ERROR 级别
 * - 普通请求 → DEBUG 级别（在 INFO 配置下默认不输出，避免日志爆量）
 * <p>
 * 注意：不记录请求体（避免敏感信息泄露与流消耗），仅记录元数据。
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    /** 慢接口阈值（毫秒） */
    private static final long SLOW_THRESHOLD_MS = 500L;

    /** 关键链路 URI 前缀（命中即按 INFO 级别记录） */
    private static final Set<String> CRITICAL_PATH_PREFIXES = Set.of(
            "/api/v1/auth/login",             // 用户登录
            "/api/v1/auth/logout",            // 用户登出
            "/api/v1/admin/login",            // 管理员登录
            "/api/v1/admin/user-info",        // 管理员登录态恢复
            "/api/v1/pay/",                   // 支付（下单/回调）
            "/api/v1/user/orders",            // 用户端订单（创建/支付）
            "/api/v1/admin/entry/",           // 入场核销
            "/api/v1/admin/memberships/",     // 管理端会员卡（激活/停用）
            "/api/v1/user/memberships/"       // 用户端会员卡（激活）
    );

    /** 请求开始时间在 request attribute 中的 key */
    private static final String ATTR_START_TIME = "__request_start_time__";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long start = (long) request.getAttribute(ATTR_START_TIME);
        long cost = System.currentTimeMillis() - start;
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUri = query == null ? uri : uri + "?" + query;

        String handlerName = resolveHandler(handler);
        String caller = resolveCaller();

        boolean critical = isCritical(uri);
        boolean slow = cost >= SLOW_THRESHOLD_MS;
        boolean serverError = status >= 500;
        boolean clientError = status >= 400 && status < 500;

        // 服务端错误：ERROR
        if (serverError) {
            log.error("[REQ] {} {} -> {} ({}ms, caller={}, handler={}, ex={})",
                    method, fullUri, status, cost, caller, handlerName,
                    ex == null ? "-" : ex.getClass().getSimpleName());
            return;
        }
        // 慢接口：WARN（关键链路也是 WARN，因为慢本身就是问题）
        if (slow) {
            log.warn("[SLOW] {} {} -> {} ({}ms, caller={}, handler={})",
                    method, fullUri, status, cost, caller, handlerName);
            return;
        }
        // 客户端错误：WARN（4xx 需要关注但不致命）
        if (clientError) {
            log.warn("[REQ] {} {} -> {} ({}ms, caller={}, handler={})",
                    method, fullUri, status, cost, caller, handlerName);
            return;
        }
        // 关键链路：INFO
        if (critical) {
            log.info("[REQ] {} {} -> {} ({}ms, caller={}, handler={})",
                    method, fullUri, status, cost, caller, handlerName);
            return;
        }
        // 普通请求：DEBUG（INFO 配置下默认不输出）
        log.debug("[REQ] {} {} -> {} ({}ms, caller={}, handler={})",
                method, fullUri, status, cost, caller, handlerName);
    }

    /**
     * 解析 Controller 类名#方法名（用于定位代码）
     */
    private String resolveHandler(Object handler) {
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
        }
        return handler == null ? "-" : handler.getClass().getSimpleName();
    }

    /**
     * 从 SecurityContext 解析当前调用者 ID（用户 ID 或管理员 ID）
     */
    private String resolveCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        // JwtAuthenticationFilter 中 principal 直接是 user/admin 的 id（Long）
        return principal.toString();
    }

    /**
     * 判断是否为关键链路（登录/支付/激活/核销等）
     */
    private boolean isCritical(String uri) {
        if (uri == null) return false;
        for (String prefix : CRITICAL_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
