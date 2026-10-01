package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Admin;
import com.xiaoshan.fitness.mapper.AdminMapper;
import com.xiaoshan.fitness.service.AuditLogService;
import com.xiaoshan.fitness.service.RiskControlService;
import com.xiaoshan.fitness.util.CacheService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.vo.AdminInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端认证接口
 * 账号密码登录：查 admins 表，BCrypt 比对密码，签发 JWT（type=admin）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;
    private final RiskControlService riskControlService;
    private final AuditLogService auditLogService;

    /** 管理员信息缓存 TTL：60 秒（短 TTL，管理员信息变更虽无入口但保守起见） */
    private static final Duration CACHE_TTL_ADMIN_INFO = Duration.ofSeconds(60);

    /**
     * 管理员账号密码登录
     * 返回 token/expiresIn/expiresAt/type/adminInfo，并写 Cookie
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse response) {
        long start = System.currentTimeMillis();
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.fail("账号或密码不能为空");
        }
        String ip = resolveIp(httpRequest);

        Admin admin = adminMapper.findByUsername(username).orElse(null);
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            // 只记用户名，绝不记录密码
            log.warn("[登录] 管理员登录失败：{}", username);
            // 风控：失败爆发检测；审计：记录一次失败登录
            riskControlService.recordAdminLoginFail(username, ip);
            auditLogService.recordLogin(username, false, ip, (int) (System.currentTimeMillis() - start));
            return Result.fail("账号或密码不正确");
        }

        // 生成 token + 过期详情，并写 Cookie
        Map<String, Object> tokenDetails = jwtUtil.generateTokenWithDetails(admin.getId(), JwtUtil.TYPE_ADMIN);
        jwtUtil.setAuthCookie(response, (String) tokenDetails.get("token"));

        log.info("[登录] 管理员登录成功：{} (adminId={})", admin.getUsername(), admin.getId());
        // 审计：记录一次成功登录（带用户名，登录请求尚无认证主体）
        auditLogService.recordLogin(admin.getUsername(), true, ip, (int) (System.currentTimeMillis() - start));

        Map<String, Object> adminInfo = new LinkedHashMap<>();
        adminInfo.put("id", admin.getId());
        adminInfo.put("username", admin.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokenDetails.get("token"));
        data.put("expiresIn", tokenDetails.get("expiresIn"));
        data.put("expiresAt", tokenDetails.get("expiresAt"));
        data.put("type", tokenDetails.get("type"));
        data.put("adminInfo", adminInfo);
        return Result.ok(data, "登录成功");
    }

    /**
     * 获取当前管理员信息（刷新页面恢复登录态）
     * 仅接受 type=admin 的 token，返回 adminInfo
     * <p>
     * 缓存：以 adminId 为维度，TTL=60 秒。刷新页面恢复登录态时不再每次查 DB。
     * 管理员信息当前无修改入口，缓存无需失效。
     */
    @GetMapping("/user-info")
    public Result<AdminInfoVO> getUserInfo(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return Result.fail("未登录或登录已过期");
        }

        if (!JwtUtil.TYPE_ADMIN.equals(jwtUtil.getTypeFromToken(token))) {
            return Result.fail("令牌类型不正确");
        }

        Long adminId = jwtUtil.getUserIdFromToken(token);
        // 缓存具体 VO（禁止缓存 Map<String,Object>，否则 JSON 序列化会报类型 id 错误）
        AdminInfoVO info = cacheService.cacheThrough(
                "admin:info:" + adminId,
                CACHE_TTL_ADMIN_INFO,
                () -> adminMapper.findById(adminId)
                        .map(a -> new AdminInfoVO(a.getId(), a.getUsername()))
                        .orElse(null),
                AdminInfoVO.class
        );
        if (info == null) {
            cacheService.evict("admin:info:" + adminId);
            return Result.fail("管理员不存在");
        }
        return Result.ok(info);
    }

    /** 解析真实客户端 IP（反向代理取 X-Forwarded-For 首段） */
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
