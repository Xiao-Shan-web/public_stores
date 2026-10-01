package com.xiaoshan.fitness.filter;

import com.xiaoshan.fitness.entity.Admin;
import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.mapper.AdminMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * 1. 从 Authorization 头 / Cookie 提取 Token
 * 2. 校验黑名单（Redis）
 * 3. 校验 Token 有效性
 * 4. 按 type 分流：user 查 users 表，admin 查 admins 表
 * 5. 写入 SecurityContext
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 0. 认证接口直接放行（登录 / 获取手机号 / 获取用户信息 / 登出）
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/auth/") || uri.startsWith("/api/v1/admin/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 从 Authorization 头 / Cookie 提取 Token
        String token = jwtUtil.extractToken(request);

        // 2. 校验黑名单（用户在其他设备登录则旧 Token 失效）
        if (token != null) {
            String blacklisted = stringRedisTemplate.opsForValue().get("jwt:blacklist:" + token);
            if (blacklisted != null) {
                log.warn("token已被加入黑名单，用户在其他设备登录");
                clearAuthCookie(response);
                sendErrorResponse(response, "账号在其他设备登录，请重新登录");
                return;
            }
        }

        // 3. 校验 Token 有效性并加载身份
        if (token != null && jwtUtil.validateToken(token)) {
            try {
                Long id = jwtUtil.getUserIdFromToken(token);
                String type = jwtUtil.getTypeFromToken(token);
                Object principal;

                if (JwtUtil.TYPE_ADMIN.equals(type)) {
                    // 管理员 token：查 admins 表
                    Admin admin = adminMapper.findById(id)
                            .orElseThrow(() -> new RuntimeException("管理员不存在"));
                    principal = admin.getId();
                } else {
                    // 普通用户 token：查 users 表
                    User user = userMapper.findById(id)
                            .orElseThrow(() -> {
                                log.warn("用户不存在，用户ID: {}", id);
                                return new RuntimeException("用户不存在");
                            });
                    // status：1-正常，0-禁用（null 兜底按禁用处理，避免 NPE）
                    if (user.getStatus() == null || user.getStatus() != 1) {
                        log.warn("用户: {} 账号已被禁用", id);
                        clearAuthCookie(response);
                        sendErrorResponse(response, "账号已被禁用");
                        return;
                    }
                    principal = user.getId();
                }

                // 4. 写入 SecurityContext（仅 Token 验证，角色由分表区分）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (RuntimeException e) {
                // token 签名有效但身份加载失败（用户/管理员已删除）：
                // 属于数据状态问题，记 WARN；响应统一友好提示，不回传内部信息
                log.warn("JWT身份加载失败: {}", e.getMessage());
                clearAuthCookie(response);
                sendErrorResponse(response, "登录状态已失效，请重新登录");
                return;
            }
        }

        // 5. 继续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 清除认证 Cookie
     */
    private void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(JwtUtil.AUTH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 发送认证错误响应
     * 统一响应体：{ success, message, code }，与 GlobalExceptionHandler / Result 对齐；
     * message 仅含友好提示（本类所有调用点均为固定文案，不含内部异常细节）
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\": false, \"message\": \"" + message + "\", \"code\": 401}");
        response.getWriter().flush();
    }

}
