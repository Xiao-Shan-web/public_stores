package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.mapper.ActivityMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户端限时活动接口
 * GET /api/v1/user/activities        进行中的活动列表
 * GET /api/v1/user/activities/{id}   活动详情
 */
@RestController
@RequestMapping("/api/v1/user/activities")
@RequiredArgsConstructor
@Slf4j
public class UserActivityController {

    private final JwtUtil jwtUtil;
    private final ActivityMapper activityMapper;

    /** 进行中活动列表（登录可见） */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", activityMapper.findOngoing());
        return Result.ok(data);
    }

    /** 活动详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> a = activityMapper.findDetail(id);
        if (a == null) {
            return Result.fail(404, "活动不存在");
        }
        return Result.ok(a);
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

}
