package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Complaint;
import com.xiaoshan.fitness.service.ComplaintService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端投诉接口
 * <ul>
 *   <li>POST /api/v1/user/complaints       提交投诉</li>
 *   <li>GET  /api/v1/user/complaints       我的投诉列表（分页）</li>
 *   <li>GET  /api/v1/user/complaints/{id}  投诉详情（含仲裁进度，仅本人）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/user/complaints")
@RequiredArgsConstructor
@Slf4j
public class ComplaintController {

    private final ComplaintService complaintService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public Result<Complaint> submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) return Result.fail(401, "未登录或登录已过期");

        @SuppressWarnings("unchecked")
        List<String> images = body.get("images") instanceof List<?> l ? (List<String>) l : null;
        Complaint c = complaintService.submit(
                userId,
                str(body.get("type")),
                str(body.get("title")),
                str(body.get("content")),
                images,
                str(body.get("bizType")),
                str(body.get("bizId")),
                longVal(body.get("storeId")),
                str(body.get("contactPhone")));
        return Result.ok(c, "投诉已提交，平台将尽快处理");
    }

    @GetMapping
    public Result<Map<String, Object>> myList(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.myList(userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.detailForUser(userId, id));
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) return null;
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) return null;
        return jwtUtil.getUserIdFromToken(token);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private Long longVal(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
