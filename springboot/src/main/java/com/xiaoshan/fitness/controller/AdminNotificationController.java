package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.service.AdminNotificationService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端系统通知接口
 * <p>
 * 管理员给全体用户或指定用户发送通知；查看已发通知批次；按批次删除。
 * 用户端接收/已读见 {@link MessageController}（/api/v1/message/*），实时推送走 WebSocket。
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationController {

    private final JwtUtil jwtUtil;
    private final AdminNotificationService adminNotificationService;
    private final UserMapper userMapper;

    /**
     * 创建并发送通知
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody NotificationCreateDTO dto,
                                              HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail("未登录或登录已过期");
        }

        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (title.isEmpty()) {
            return Result.fail("通知标题不能为空");
        }
        if (content.isEmpty()) {
            return Result.fail("通知内容不能为空");
        }

        String type = dto.getType() == null || dto.getType().isBlank() ? "SYSTEM" : dto.getType().trim();
        // 发送范围：ALL 全部 / SPECIFIED 指定用户 / SEGMENT 分人群（按卡类型或门店）
        String scope;
        if (AdminNotificationService.SCOPE_ALL.equalsIgnoreCase(dto.getScope())) {
            scope = AdminNotificationService.SCOPE_ALL;
        } else if (AdminNotificationService.SCOPE_SEGMENT.equalsIgnoreCase(dto.getScope())) {
            scope = AdminNotificationService.SCOPE_SEGMENT;
        } else {
            scope = AdminNotificationService.SCOPE_SPECIFIED;
        }
        List<String> receivers = dto.getReceivers();

        if (AdminNotificationService.SCOPE_SPECIFIED.equals(scope)
                && (receivers == null || receivers.isEmpty())) {
            return Result.fail(400, "指定用户发送时需填写至少一个用户ID或手机号");
        }
        if (AdminNotificationService.SCOPE_SEGMENT.equals(scope)
                && dto.getCardTypeId() == null && dto.getStoreId() == null) {
            return Result.fail(400, "分人群发送时需至少选择一个卡类型或门店（不选等同于全部用户）");
        }

        Map<String, Object> data = adminNotificationService.send(
                adminId, type, title, content, scope, receivers, dto.getCardTypeId(), dto.getStoreId());

        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) data.get("missing");
        int receiverCount = (int) data.get("receiverCount");
        if (receiverCount == 0) {
            return Result.fail(400, "未匹配到任何有效用户，请检查筛选条件或用户ID/手机号");
        }
        String msg = "发送成功，共 " + receiverCount + " 个用户";
        if (missing != null && !missing.isEmpty()) {
            msg += "；以下 " + missing.size() + " 个标识未匹配：" + String.join("、", missing);
        }
        return Result.ok(data, msg);
    }

    /**
     * 群发人群预览：各投放范围预计触达人数
     */
    @GetMapping("/audience-preview")
    public Result<Map<String, Object>> audiencePreview(
            @RequestParam(required = false) Long cardTypeId,
            @RequestParam(required = false) Long storeId,
            HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", userMapper.countAll(""));
        data.put("cardTypeCount", cardTypeId == null ? null : userMapper.countByCardType(cardTypeId));
        data.put("storeCount", storeId == null ? null : userMapper.countByStore(storeId));
        if (cardTypeId != null || storeId != null) {
            data.put("segmentCount", userMapper.findIdsBySegment(cardTypeId, storeId).size());
        }
        return Result.ok(data);
    }

    /**
     * 已发通知批次列表（分页）
     */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail("未登录或登录已过期");
        }
        return Result.ok(adminNotificationService.listBatches(adminId, type, page, size));
    }

    /**
     * 按批次删除整组通知（id 为批次 batchId）
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail("未登录或登录已过期");
        }
        int rows = adminNotificationService.deleteBatch(adminId, id);
        return Result.ok("已删除 " + rows + " 条通知记录");
    }

    /**
     * 从请求解析当前管理员ID
     */
    private Long currentAdminId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_ADMIN.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * 新建通知请求体
     */
    @Data
    public static class NotificationCreateDTO {
        /** 通知类型：SYSTEM 系统公告 / ACTIVITY 活动通知 / VERIFY / EXPIRE */
        private String type;
        /** 通知标题（必填） */
        private String title;
        /** 通知内容（必填） */
        private String content;
        /** 发送范围：ALL 全部用户 / SPECIFIED 指定用户 / SEGMENT 分人群 */
        private String scope;
        /** 指定发送时的接收者标识（用户ID 或 手机号） */
        private List<String> receivers;
        /** 分人群：持有该卡类型的用户 */
        private Long cardTypeId;
        /** 分人群：持有该门店卡的用户 */
        private Long storeId;
    }

}
