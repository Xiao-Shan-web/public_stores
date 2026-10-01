package com.xiaoshan.fitness.interceptor;

import com.xiaoshan.fitness.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 管理员操作审计拦截器。
 * <p>
 * 拦截 /api/v1/admin/** 的写操作（POST/PUT/DELETE/PATCH），在 afterCompletion
 * 异步落一条审计日志（谁、在什么模块、做了什么动作、结果、耗时、IP）。
 * <ul>
 *   <li>只读 GET 不审计（查询不留痕，控制日志体量）；</li>
 *   <li>登录接口 /admin/login 不在此记录（请求时无认证主体），由 AdminAuthController 显式记录；</li>
 *   <li>不读取请求体（避免流消耗），操作对象从 URI 路径/query 定位，query 敏感参数脱敏；</li>
 *   <li>审计本身任何异常都不影响业务请求。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "__audit_start__";
    private static final String ATTR_ADMIN_ID = "__audit_admin_id__";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    /** query 中需要脱敏的参数关键字（包含即遮蔽其值） */
    private static final String[] SENSITIVE_KEYS = {"password", "passwd", "token", "secret"};

    private final AuditLogService auditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod && shouldAudit(request)) {
            request.setAttribute(ATTR_START, System.currentTimeMillis());
            request.setAttribute(ATTR_ADMIN_ID, resolveAdminId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (request.getAttribute(ATTR_START) == null) return;
        try {
            long cost = System.currentTimeMillis() - (long) request.getAttribute(ATTR_START);
            Long adminId = (Long) request.getAttribute(ATTR_ADMIN_ID);
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = sanitizeQuery(request.getQueryString());
            String paramSummary = query;
            boolean success = ex == null && response.getStatus() < 400;

            auditLogService.recordAsync(adminId, resolveModule(uri), method, uri,
                    paramSummary, resolveIp(request), success, (int) cost);
        } catch (Exception e) {
            log.debug("审计拦截器记录异常（忽略）: {}", e.getMessage());
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) return false;
        // 登录由 Controller 显式记录，避免无主体的空日志
        return !"/api/v1/admin/login".equals(request.getRequestURI());
    }

    /** 从 URI 推导业务模块：/api/v1/admin/coupons/123 → coupons */
    private String resolveModule(String uri) {
        String prefix = "/api/v1/admin/";
        if (uri != null && uri.startsWith(prefix)) {
            String rest = uri.substring(prefix.length());
            int slash = rest.indexOf('/');
            String seg = slash > 0 ? rest.substring(0, slash) : rest;
            return seg.isBlank() ? "index" : seg;
        }
        return "admin";
    }

    private Long resolveAdminId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
            Object p = auth.getPrincipal();
            if ("anonymousUser".equals(p.toString())) return null;
            if (p instanceof Long l) return l;
            return Long.valueOf(p.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** query 敏感参数脱敏：key=value → key=*** */
    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String[] pairs = query.split("&");
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            String low = key.toLowerCase();
            boolean sensitive = false;
            for (String s : SENSITIVE_KEYS) {
                if (low.contains(s)) { sensitive = true; break; }
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(sensitive ? key + "=***" : pair);
        }
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }

    /** 解析真实客户端 IP（反向代理场景取 X-Forwarded-For 首段） */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
