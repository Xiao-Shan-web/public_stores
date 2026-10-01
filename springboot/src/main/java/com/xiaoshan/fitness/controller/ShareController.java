package com.xiaoshan.fitness.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.entity.Share;
import com.xiaoshan.fitness.entity.ShareComment;
import com.xiaoshan.fitness.entity.ShareLike;
import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.entity.UserProfile;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.mapper.ShareCommentMapper;
import com.xiaoshan.fitness.mapper.ShareLikeMapper;
import com.xiaoshan.fitness.mapper.ShareMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.mapper.UserProfileMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SensitiveWordService;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员分享接口
 * 发布视频要求当前用户会员卡为 ACTIVE 状态，未开通 → 提示开通后即可分享
 * 列表/点赞/评论：仅需登录
 * <p>
 * 媒体：videoUrl 视频与 images 图片（≤9张）二选一或共存（video 优先展示）；
 * 上传走 /share/upload/**（图片压缩、视频分片），此处只存 URL。
 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
@Slf4j
public class ShareController {

    private final JwtUtil jwtUtil;
    private final ShareMapper shareMapper;
    private final ShareLikeMapper shareLikeMapper;
    private final ShareCommentMapper shareCommentMapper;
    private final MembershipMapper membershipMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 单条分享图片上限 */
    private static final int MAX_IMAGES = 9;
    /** 浏览数去重时间窗（同一用户对同一分享窗口期内只计一次） */
    private static final long VIEW_DEDUPE_MS = 60_000L;

    /**
     * 分享列表（分页，按时间倒序）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        int offset = Math.max(0, (page - 1) * size);
        List<Share> shares = shareMapper.findPage(offset, size);
        long total = shareMapper.countAll();

        List<Map<String, Object>> list = shares.stream()
                .map(s -> buildShareVO(s, userId))
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 我的分享列表
     */
    @GetMapping("/my")
    public Result<Map<String, Object>> my(HttpServletRequest request,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        int offset = Math.max(0, (page - 1) * size);
        List<Share> shares = shareMapper.findPageByUserId(userId, offset, size);
        long total = shareMapper.countByUserId(userId);

        List<Map<String, Object>> list = shares.stream()
                .map(s -> buildShareVO(s, userId))
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 发布分享（仅 ACTIVE 状态会员卡用户可发布）
     * 未开通 → 提示"开通会员卡后即可分享"
     * <p>
     * 媒体：videoUrl（视频）与 images（图片数组，≤9张，须为 /uploads/ 域内地址）可选；
     * 标题/正文/图片URL 经敏感词审核，命中直接拒绝发布。
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        String title = (String) body.get("title");
        if (title == null || title.isBlank()) {
            return Result.fail(400, "标题不能为空");
        }
        String content = (String) body.get("content");
        String videoUrl = (String) body.get("videoUrl");
        String coverUrl = (String) body.get("coverUrl");

        // 敏感词审核前置（标题 + 正文）：无论是否会员，违规内容一律先拦截
        List<String> hits = sensitiveWordService.check(title + "\n" + (content == null ? "" : content));
        if (!hits.isEmpty()) {
            log.warn("用户{}发布分享被审核拦截：命中{}", userId, hits);
            return Result.fail(400, "内容包含违规词汇，请修改后再发布");
        }

        // 校验会员卡状态
        Membership membership = membershipMapper.findLatestByUserId(userId).orElse(null);
        boolean active = membership != null && "ACTIVE".equals(membership.getStatus());
        if (!active) {
            return Result.fail(400, "开通会员卡后即可分享");
        }

        // 图片 URL 白名单校验：仅接受本服务上传目录内的地址，防止外链注入
        List<String> images = parseImages(body.get("images"));
        if (images.size() > MAX_IMAGES) {
            return Result.fail(400, "图片最多 " + MAX_IMAGES + " 张");
        }

        Share share = new Share();
        share.setId(idGenerator.nextId());
        share.setUserId(userId);
        share.setTitle(title.trim());
        share.setContent(content);
        share.setVideoUrl(videoUrl);
        share.setCoverUrl(coverUrl);
        share.setImages(images.isEmpty() ? null : writeJson(images));
        shareMapper.insert(share);

        log.info("用户{}发布分享：{}（ID={}，图片{}张，视频={}）",
                userId, share.getTitle(), share.getId(), images.size(), videoUrl != null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", share.getId());
        data.put("userId", share.getUserId());
        data.put("title", share.getTitle());
        data.put("content", share.getContent());
        data.put("videoUrl", share.getVideoUrl());
        data.put("coverUrl", share.getCoverUrl());
        data.put("images", images);
        data.put("imagesThumb", images.stream().map(this::toThumbUrl).collect(Collectors.toList()));
        data.put("likeCount", 0);
        data.put("commentCount", 0);
        data.put("viewCount", 0);
        return Result.ok(data, "发布成功");
    }

    /**
     * 浏览数 +1（同一用户 60 秒窗口内对同一分享只计一次；未登录每次都计）
     * 前端在详情展开/视频起播时调用一次，本地按 counted 自增展示
     */
    @PostMapping("/{id}/view")
    public Result<Map<String, Object>> view(@PathVariable Long id, HttpServletRequest request) {
        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在");
        }
        boolean counted = true;
        Long userId = currentUserId(request);
        if (userId != null) {
            String key = "share:view:" + id + ":" + userId;
            try {
                counted = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, "1", Duration.ofMillis(VIEW_DEDUPE_MS)));
            } catch (Exception e) {
                // Redis 异常降级为每次都计数，不影响主流程
                log.warn("浏览数去重失败，按每次计数处理：{}", e.getMessage());
            }
        }
        if (counted) {
            shareMapper.incrViewCount(id);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("counted", counted);
        return Result.ok(data);
    }

    /**
     * 解析并校验 images 入参：仅接受字符串数组，且必须以 /uploads/ 开头
     */
    private List<String> parseImages(Object raw) {
        List<String> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof String s && s.startsWith("/uploads/") && !s.contains("..")) {
                result.add(s);
            }
        }
        return result;
    }

    private String writeJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.error("图片列表序列化失败", e);
            return null;
        }
    }

    /**
     * 点赞 / 取消点赞（toggle）
     * 已点赞 → 取消（like_count -1）
     * 未点赞 → 新增（like_count +1）
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long id,
                                                  HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在");
        }

        int existed = shareLikeMapper.exists(id, userId);
        boolean liked;
        if (existed > 0) {
            shareLikeMapper.delete(id, userId);
            shareMapper.decrLikeCount(id);
            liked = false;
            log.info("用户{}取消点赞分享{}", userId, id);
        } else {
            ShareLike like = new ShareLike();
            like.setId(idGenerator.nextId());
            like.setShareId(id);
            like.setUserId(userId);
            shareLikeMapper.insert(like);
            shareMapper.incrLikeCount(id);
            liked = true;
            log.info("用户{}点赞分享{}", userId, id);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liked", liked);
        return Result.ok(data, liked ? "点赞成功" : "已取消点赞");
    }

    /**
     * 某分享的评论列表（分页）
     */
    @GetMapping("/{id}/comments")
    public Result<Map<String, Object>> comments(@PathVariable Long id,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<ShareComment> comments = shareCommentMapper.findByShareId(id, offset, size);
        long total = shareCommentMapper.countByShareId(id);

        List<Map<String, Object>> list = comments.stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.getId());
                    item.put("shareId", c.getShareId());
                    item.put("userId", c.getUserId());
                    item.put("authorNickname", c.getAuthorNickname());
                    item.put("authorAvatar", c.getAuthorAvatar());
                    item.put("authorAvatarThumb", c.getAuthorAvatarThumb());
                    // 展示名：昵称优先，无昵称回退打码手机号，再无则「山达会员」
                    item.put("authorName",
                            resolveAuthorName(c.getAuthorNickname(), c.getAuthorPhone()));
                    item.put("authorPhone", maskPhone(c.getAuthorPhone()));
                    item.put("content", c.getContent());
                    item.put("createdAt", c.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 发表评论（仅要求登录，不强制 ACTIVE 会员卡）
     */
    @PostMapping("/{id}/comment")
    public Result<Map<String, Object>> comment(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Share share = shareMapper.findById(id);
        if (share == null) {
            return Result.fail(404, "分享不存在");
        }

        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Result.fail(400, "评论内容不能为空");
        }

        // 敏感词审核
        List<String> hits = sensitiveWordService.check(content);
        if (!hits.isEmpty()) {
            log.warn("用户{}评论被审核拦截：命中{}", userId, hits);
            return Result.fail(400, "评论包含违规词汇，请修改后再发表");
        }

        ShareComment comment = new ShareComment();
        comment.setId(idGenerator.nextId());
        comment.setShareId(id);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        shareCommentMapper.insert(comment);
        shareMapper.incrCommentCount(id);

        log.info("用户{}评论分享{}", userId, id);

        // 回包带上评论者昵称/头像/展示名：前端发表成功后本地追加评论可直接渲染（含头像）
        String authorPhone = userMapper.findById(userId).map(User::getPhone).orElse(null);
        UserProfile profile = userProfileMapper.findByUserId(userId).orElse(null);
        String authorNickname = profile == null ? null : profile.getNickname();
        String authorAvatar = profile == null ? null : profile.getAvatar();
        String authorAvatarThumb = profile == null ? null : profile.getAvatarThumb();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", comment.getId());
        data.put("shareId", comment.getShareId());
        data.put("userId", comment.getUserId());
        data.put("authorNickname", authorNickname);
        data.put("authorAvatar", authorAvatar);
        data.put("authorAvatarThumb", authorAvatarThumb);
        data.put("authorName", resolveAuthorName(authorNickname, authorPhone));
        data.put("authorPhone", maskPhone(authorPhone));
        data.put("content", comment.getContent());
        data.put("createdAt", comment.getCreatedAt());
        return Result.ok(data, "评论成功");
    }

    /**
     * 从请求解析当前用户ID（仅接受普通用户 token）
     */
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

    /**
     * 构建分享视图对象（含当前用户是否已点赞 liked 字段、图片列表、浏览数）
     */
    private Map<String, Object> buildShareVO(Share s, Long currentUserId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", s.getId());
        item.put("userId", s.getUserId());
        item.put("authorPhone", maskPhone(s.getAuthorPhone()));
        item.put("authorAvatar", s.getAuthorAvatar());
        item.put("authorAvatarThumb", s.getAuthorAvatarThumb());
        item.put("title", s.getTitle());
        item.put("content", s.getContent());
        item.put("videoUrl", s.getVideoUrl());
        item.put("coverUrl", s.getCoverUrl());
        List<String> images = parseImagesJson(s.getImages());
        item.put("images", images);
        // 列表展示用缩略图（约定 _thumb.jpg），减少列表原图流量；GIF/历史数据由前端 onerror 回退原图
        item.put("imagesThumb", images.stream().map(this::toThumbUrl).collect(Collectors.toList()));
        item.put("likeCount", s.getLikeCount() != null ? s.getLikeCount() : 0);
        item.put("commentCount", s.getCommentCount() != null ? s.getCommentCount() : 0);
        item.put("viewCount", s.getViewCount() != null ? s.getViewCount() : 0);
        item.put("liked", currentUserId != null && shareMapper.countLikeByUser(s.getId(), currentUserId) > 0);
        item.put("createdAt", s.getCreatedAt());
        return item;
    }

    /**
     * images JSON 字符串 → 列表（历史数据/脏数据容错：解析失败返回空列表）
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

    /**
     * 由原图地址推导缩略图地址（约定：同名 _thumb.jpg）。
     * GIF 无缩略图，返回原图；历史数据无缩略图文件时由前端 onerror 回退原图。
     */
    private String toThumbUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.toLowerCase().endsWith(".gif")) return imageUrl;
        int dot = imageUrl.lastIndexOf('.');
        if (dot <= 0) return imageUrl;
        return imageUrl.substring(0, dot) + "_thumb.jpg";
    }

    /**
     * 手机号脱敏（中间4位用 * 替换，保护用户隐私）
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "用户";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 评论展示名解析：昵称（非空白）优先；否则回退打码手机号；再无则「山达会员」。
     */
    private String resolveAuthorName(String nickname, String phone) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        if (phone != null && phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return "山达会员";
    }

}
