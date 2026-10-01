package com.xiaoshan.fitness.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 工具类
 * - generateToken(id, type)：30 天有效期
 * - generateTokenWithDetails(id, type)：返回 token + expiresIn + expiresAt + type
 * - extractToken：优先 Authorization: Bearer，回退 Cookie "token"
 * - setAuthCookie：H5 环境写 Cookie（httpOnly + sameSite=Strict + maxAge=30天）
 * <p>
 * 普通用户 token subject=users.id type=user
 * 管理员 token subject=admins.id type=admin
 */
@Component
@Slf4j
public class JwtUtil {

    /** Cookie 名称（H5 回退渠道，主渠道为 Authorization 头） */
    public static final String AUTH_COOKIE_NAME = "token";

    /** 普通用户类型 */
    public static final String TYPE_USER = "user";
    /** 管理员类型 */
    public static final String TYPE_ADMIN = "admin";

    /** Token 有效期：30 天（秒） */
    public static final long TOKEN_VALIDITY_SECONDS = 30L * 24 * 60 * 60;

    @Value("${jwt.secret}")
    private String secret;

    /** 生产环境通过 app.cookie.secure=true 开启 Secure（需 HTTPS） */
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * 生成 Token（有效期 7 天）
     */
    public String generateToken(Long id, String type) {
        ZonedDateTime now = ZonedDateTime.now();
        Date expirationDate = Date.from(now.plusSeconds(TOKEN_VALIDITY_SECONDS).toInstant());

        return Jwts.builder()
                .setSubject(id.toString())
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .claim("type", type)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /**
     * 生成 Token 并附带过期信息（登录响应用）
     *
     * @return { token, expiresIn(秒), expiresAt(毫秒), type }
     */
    public Map<String, Object> generateTokenWithDetails(Long id, String type) {
        String token = generateToken(id, type);
        long expiresAt = System.currentTimeMillis() + TOKEN_VALIDITY_SECONDS * 1000L;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("token", token);
        details.put("expiresIn", TOKEN_VALIDITY_SECONDS);
        details.put("expiresAt", expiresAt);
        details.put("type", type);
        return details;
    }

    /**
     * 写入认证 Cookie（H5 环境浏览器自动携带；APK 环境用 Authorization 头）
     * httpOnly 防 XSS，sameSite=Strict 防 CSRF，maxAge 7 天
     */
    public void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .maxAge(TOKEN_VALIDITY_SECONDS)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 从请求中提取 Token
     * 1. 优先 Authorization: Bearer xxx（前端 Axios 拦截器注入）
     * 2. 回退 Cookie "token"
     */
    public String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return extractTokenFromCookie(request);
    }

    /**
     * 从 Cookie 提取 Token
     */
    public String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 清除认证 Cookie（登出 / token 失效）
     */
    public void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 校验 Token 有效性
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 Token 中获取ID（subject）
     */
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody()
                .getSubject());
    }

    /**
     * 从 Token 中获取类型（user / admin）
     */
    public String getTypeFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
            String type = claims.get("type", String.class);
            return type != null ? type : TYPE_USER;
        } catch (Exception e) {
            log.warn("从Token解析类型失败: {}", e.getMessage());
            return null;
        }
    }

}
