package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.mapper.CustomerServiceMessageMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端客服消息接口
 */
@RestController
@RequestMapping("/api/v1/admin/service/messages")
@RequiredArgsConstructor
@Slf4j
public class AdminServiceController {

    private final JwtUtil jwtUtil;
    private final CustomerServiceMessageMapper messageMapper;

    /**
     * 将指定用户发来的全部未读消息标记为已读
     * （管理员进入会话 / 停留会话收到新消息时调用）
     */
    @PostMapping("/read-all/{userId}")
    public Result<String> readAll(@PathVariable Long userId, HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return Result.fail("未登录或登录已过期");
        }
        if (!JwtUtil.TYPE_ADMIN.equals(jwtUtil.getTypeFromToken(token))) {
            return Result.fail("仅客服可操作");
        }
        messageMapper.markReadForAdmin(userId);
        log.info("客服{}标记用户{}的客服消息全部已读", jwtUtil.getUserIdFromToken(token), userId);
        return Result.ok("已读");
    }

}
