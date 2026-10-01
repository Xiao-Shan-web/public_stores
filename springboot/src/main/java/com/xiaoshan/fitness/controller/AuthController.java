package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.entity.UserProfile;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.mapper.UserProfileMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户端认证接口
 * 手机号一键登录：查询 users 表，不存在则自动创建，签发 JWT（type=user）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final JwtUtil jwtUtil;

    /**
     * 手机号一键登录
     * 1. 严格校验手机号格式（中国大陆：1 开头，第二位 3-9，共 11 位数字）
     * 2. 查 users 表，不存在则自动创建
     * 3. 签发 JWT 返回 token/expiresIn/expiresAt/type/userInfo，并写 Cookie
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                             HttpServletResponse response) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号格式不正确");
        }

        // 查询或创建用户
        boolean[] isNewUser = {false};
        User user = userMapper.findByPhone(phone).orElseGet(() -> {
            User u = new User();
            u.setId(idGenerator.nextId());
            u.setPhone(phone);
            u.setStatus(1);
            userMapper.insert(u);
            isNewUser[0] = true;
            return u;
        });
        if (isNewUser[0]) {
            log.info("[登录] 新用户注册：{}", maskPhone(phone));
        }

        // 禁用账号拒绝登录（status：1-正常，0-禁用）
        if (Integer.valueOf(0).equals(user.getStatus())) {
            log.warn("[登录] 禁用账号尝试登录：{}", maskPhone(phone));
            return Result.fail("账号已被禁用，请联系管理员");
        }

        // 生成 token + 过期详情，并写 H5 Cookie（APK 端用 Authorization 头）
        Map<String, Object> tokenDetails = jwtUtil.generateTokenWithDetails(user.getId(), JwtUtil.TYPE_USER);
        jwtUtil.setAuthCookie(response, (String) tokenDetails.get("token"));

        log.info("[登录] 用户登录成功：{} (userId={})", maskPhone(phone), user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokenDetails.get("token"));
        data.put("expiresIn", tokenDetails.get("expiresIn"));
        data.put("expiresAt", tokenDetails.get("expiresAt"));
        data.put("type", tokenDetails.get("type"));
        data.put("userInfo", buildUserInfo(user));
        return Result.ok(data, "登录成功");
    }

    /**
     * 获取当前用户信息（刷新页面恢复登录态）
     * 从 Authorization 头解析 token → 查 users 表
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return Result.fail("未登录或登录已过期");
        }

        // 仅接受普通用户 token
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return Result.fail("令牌类型不正确");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userMapper.findById(userId).orElse(null);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            return Result.fail("账号已被禁用");
        }
        return Result.ok(buildUserInfo(user));
    }

    /**
     * 退出登录（清除 Cookie，客户端清除 token 即可，服务端无状态）
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletResponse response) {
        jwtUtil.clearAuthCookie(response);
        return Result.ok("已退出登录");
    }

    /**
     * 手机号脱敏（中间4位用 * 替换，日志中不输出完整手机号）
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 构建用户信息响应（真实 DB 数据，无 memberCard 时前端按"未开通"展示）
     * 附带 user_profiles 中的头像与昵称，供首页/我的页直接展示
     */
    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("phone", user.getPhone());
        info.put("appId", user.getAppId());
        info.put("status", user.getStatus());
        UserProfile profile = userProfileMapper.findByUserId(user.getId()).orElse(null);
        info.put("avatar", profile == null ? null : profile.getAvatar());
        info.put("nickname", profile == null ? null : profile.getNickname());
        return info;
    }

}
