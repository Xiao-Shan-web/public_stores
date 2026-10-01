package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.Share;
import com.xiaoshan.fitness.entity.ShareComment;
import com.xiaoshan.fitness.mapper.ShareCommentMapper;
import com.xiaoshan.fitness.mapper.ShareMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端分享内容管理接口（分享广场 / 分享评论的审核与下架）
 * <p>
 * 用户端分享列表只读取 status='NORMAL'，因此「隐藏」即等于用户端不可见，无需额外联动。
 * <ul>
 *   <li>GET    /api/v1/admin/shares                            分享列表（含已隐藏；分页 / 状态 / 关键字）</li>
 *   <li>PUT    /api/v1/admin/shares/{id}/status                隐藏 / 恢复</li>
 *   <li>DELETE /api/v1/admin/shares/{id}                       删除分享（连带清理评论与点赞）</li>
 *   <li>GET    /api/v1/admin/shares/{id}/comments              评论列表（分页）</li>
 *   <li>DELETE /api/v1/admin/shares/{id}/comments/{commentId}  删除单条评论（同步评论数）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/shares")
@RequiredArgsConstructor
@Slf4j
public class AdminShareController {

    /** 正常（用户端可见） */
    private static final String STATUS_NORMAL = "NORMAL";
    /** 已隐藏（用户端不可见） */
    private static final String STATUS_HIDDEN = "HIDDEN";

    private final JwtUtil jwtUtil;
    private final ShareMapper shareMapper;
    private final ShareCommentMapper shareCommentMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分享列表：默认查全部状态，支持按状态筛选与关键字（标题/正文/作者手机号）搜索
     */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        String statusFilter = normalizeStatus(status);
        String kw = normalizeKeyword(keyword);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(0, (Math.max(page, 1) - 1) * safeSize);

        List<Share> shares = shareMapper.findAdminPage(statusFilter, kw, offset, safeSize);
        long total = shareMapper.countAdmin(statusFilter, kw);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", shares.stream().map(this::buildShareVO).collect(Collectors.toList()));
        data.put("total", total);
        // 徽标计数：不受状态筛选影响，便于管理员掌握待审核量
        data.put("normalCount", shareMapper.countByStatus(STATUS_NORMAL));
        data.put("hiddenCount", shareMapper.countByStatus(STATUS_HIDDEN));
        return Result.ok(data);
    }

    /**
     * 隐藏 / 恢复分享
     * <p>
     * 隐藏后用户端列表与「我的分享」都不再展示，实现违规内容下架。
     */
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Object raw = body.get("status");
        if (raw == null) {
            return Result.fail(400, "缺少 status 参数（NORMAL-恢复正常，HIDDEN-隐藏）");
        }
        String status = String.valueOf(raw).trim().toUpperCase();
        if (!STATUS_NORMAL.equals(status) && !STATUS_HIDDEN.equals(status)) {
            return Result.fail(400, "status 参数不正确，应为 NORMAL（恢复）或 HIDDEN（隐藏）");
        }

        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        if (shareMapper.updateStatus(id, status) == 0) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        boolean userVisible = STATUS_NORMAL.equals(status);
        String message = userVisible
                ? "已恢复展示，用户端可重新看到该分享"
                : "已隐藏，用户端不再展示该分享";

        log.info("管理员{}将分享{}置为{}（用户端可见={}）", adminId, id, status, userVisible);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", status);
        data.put("userVisible", userVisible);
        data.put("message", message);
        return Result.ok(data, message);
    }

    /**
     * 删除分享（违规内容彻底下架）
     * <p>
     * 连带清理该分享下的评论与点赞记录，避免产生孤儿数据；同时清 Redis 浏览去重键。
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        int comments = shareMapper.deleteCommentsByShareId(id);
        int likes = shareMapper.deleteLikesByShareId(id);
        int rows = shareMapper.deleteById(id);
        if (rows == 0) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        log.info("管理员{}删除分享{}（连带评论{}条、点赞{}条）", adminId, id, comments, likes);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("deletedComments", comments);
        data.put("deletedLikes", likes);
        return Result.ok(data, "已删除，用户端不再展示该分享");
    }

    /**
     * 某分享的评论列表（分页，供管理端审核）
     */
    @GetMapping("/{id}/comments")
    public Result<Map<String, Object>> comments(@PathVariable Long id,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(0, (Math.max(page, 1) - 1) * safeSize);

        List<ShareComment> comments = shareCommentMapper.findByShareId(id, offset, safeSize);
        long total = shareCommentMapper.countByShareId(id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", comments.stream().map(this::buildCommentVO).collect(Collectors.toList()));
        data.put("total", total);
        data.put("shareId", id);
        return Result.ok(data);
    }

    /**
     * 删除单条评论（评论数同步 -1）
     */
    @DeleteMapping("/{id}/comments/{commentId}")
    public Result<Map<String, Object>> deleteComment(@PathVariable Long id,
                                                     @PathVariable Long commentId,
                                                     HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        if (shareMapper.findById(id) == null) {
            return Result.fail(404, "分享不存在或已被删除，请刷新列表后重试");
        }

        int rows = shareCommentMapper.deleteByIdAndShareId(commentId, id);
        if (rows == 0) {
            return Result.fail(404, "评论不存在或已被删除，请刷新后重试");
        }

        // 评论数同步（不低于 0），避免用户端展示的评论数与实际不一致
        shareMapper.decrCommentCount(id);

        log.info("管理员{}删除分享{}的评论{}", adminId, id, commentId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shareId", id);
        data.put("commentId", commentId);
        return Result.ok(data, "评论已删除");
    }

    // ==================== 私有工具 ====================

    /**
     * 从请求解析当前管理员ID（仅接受管理员 token）
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
     * 状态参数归一化：空串/ALL 均为「全部状态」（返回 null）
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if (!STATUS_NORMAL.equals(s) && !STATUS_HIDDEN.equals(s)) {
            return null;
        }
        return s;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 管理端分享视图对象：管理端需要完整手机号定位用户，故不做脱敏
     */
    private Map<String, Object> buildShareVO(Share s) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", s.getId());
        item.put("userId", s.getUserId());
        item.put("authorPhone", s.getAuthorPhone());
        item.put("authorAvatar", s.getAuthorAvatar());
        item.put("title", s.getTitle());
        item.put("content", s.getContent());
        item.put("videoUrl", s.getVideoUrl());
        item.put("coverUrl", s.getCoverUrl());
        item.put("images", parseImagesJson(s.getImages()));
        item.put("likeCount", s.getLikeCount() != null ? s.getLikeCount() : 0);
        item.put("commentCount", s.getCommentCount() != null ? s.getCommentCount() : 0);
        item.put("viewCount", s.getViewCount() != null ? s.getViewCount() : 0);
        item.put("status", s.getStatus());
        item.put("userVisible", STATUS_NORMAL.equals(s.getStatus()));
        item.put("createdAt", s.getCreatedAt());
        return item;
    }

    /**
     * 评论视图对象：昵称优先，无昵称回退打码手机号，再无则「山达会员」
     */
    private Map<String, Object> buildCommentVO(ShareComment c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("shareId", c.getShareId());
        item.put("userId", c.getUserId());
        item.put("authorNickname", c.getAuthorNickname());
        item.put("authorAvatar", c.getAuthorAvatar());
        item.put("authorName", resolveAuthorName(c.getAuthorNickname(), c.getAuthorPhone()));
        item.put("authorPhone", c.getAuthorPhone());
        item.put("content", c.getContent());
        item.put("createdAt", c.getCreatedAt());
        return item;
    }

    private String resolveAuthorName(String nickname, String phone) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        if (phone != null && phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return "山达会员";
    }

    /**
     * images JSON 字符串 → 列表（脏数据容错：解析失败返回空列表）
     */
    private List<String> parseImagesJson(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("images 解析失败，按无图处理：{}", e.getMessage());
            return List.of();
        }
    }

}
