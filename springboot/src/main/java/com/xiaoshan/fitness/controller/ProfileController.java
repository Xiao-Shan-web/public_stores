package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.entity.UserProfile;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.mapper.UserProfileMapper;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户个人资料接口
 * GET  /api/v1/user/profile         获取当前用户资料
 * PUT  /api/v1/user/profile         更新昵称/真实姓名/性别/生日/简介
 * POST /api/v1/user/profile/avatar  上传头像（返回可访问地址并落库）
 */
@RestController
@RequestMapping("/api/v1/user/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    /** 允许上传的图片扩展名（头像不支持 gif，避免压缩丢动画） */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    /** 头像大小上限：20MB（前端 >10MB 会先压缩，后端兜底再次压缩） */
    private static final long MAX_AVATAR_SIZE = 20L * 1024 * 1024;
    /** 头像压缩后最大边长（等比缩放，主图与缩略图统一 200） */
    private static final int MAX_AVATAR_EDGE = 200;
    /** 缩略图边长（正方形） */
    private static final int THUMB_EDGE = 200;
    /** 头像压缩后目标体积上限（50KB） */
    private static final int AVATAR_TARGET_BYTES = 50 * 1024;
    /** JPEG 压缩起始质量 */
    private static final float JPEG_QUALITY_START = 0.8f;
    /** 合法性别枚举 */
    private static final Set<String> ALLOWED_GENDER = Set.of("MALE", "FEMALE", "UNKNOWN");

    /**
     * 获取当前用户资料（无资料行时返回默认值）
     */
    @GetMapping
    public Result<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        User user = userMapper.findById(userId).orElse(null);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserProfile p = userProfileMapper.findByUserId(userId).orElse(null);
        return Result.ok(buildProfileData(user, p));
    }

    /**
     * 更新当前用户资料（整页表单提交，头像不在此接口修改）
     */
    @PutMapping
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        User user = userMapper.findById(userId).orElse(null);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 字段白名单校验（不接受 avatar 等其他字段，避免越权写入）
        String nickname = trimToNull(String.valueOf(body.getOrDefault("nickname", "")));
        String realName = trimToNull(String.valueOf(body.getOrDefault("realName", "")));
        String bio = trimToNull(String.valueOf(body.getOrDefault("bio", "")));
        if (nickname != null && nickname.length() > 100) {
            return Result.fail("昵称最多 100 个字符");
        }
        if (realName != null && realName.length() > 50) {
            return Result.fail("真实姓名最多 50 个字符");
        }
        if (bio != null && bio.length() > 200) {
            return Result.fail("个人简介最多 200 个字符");
        }

        String gender = body.get("gender") == null ? "UNKNOWN" : String.valueOf(body.get("gender")).trim();
        if (gender.isEmpty()) {
            gender = "UNKNOWN";
        }
        if (!ALLOWED_GENDER.contains(gender)) {
            return Result.fail("性别参数不正确");
        }

        LocalDate birthday = null;
        Object birthdayRaw = body.get("birthday");
        if (birthdayRaw != null && !String.valueOf(birthdayRaw).isBlank()) {
            try {
                birthday = LocalDate.parse(String.valueOf(birthdayRaw).trim());
            } catch (DateTimeParseException e) {
                return Result.fail("生日格式不正确");
            }
            if (birthday.isAfter(LocalDate.now())) {
                return Result.fail("生日不能晚于今天");
            }
        }

        // upsert：已有行保留原头像，无行则以默认资料创建
        Optional<UserProfile> existed = userProfileMapper.findByUserId(userId);
        UserProfile p;
        if (existed.isPresent()) {
            p = existed.get();
            p.setNickname(nickname);
            p.setRealName(realName);
            p.setGender(gender);
            p.setBirthday(birthday);
            p.setBio(bio);
            userProfileMapper.update(p);
        } else {
            p = new UserProfile();
            p.setUserId(userId);
            p.setNickname(nickname);
            p.setRealName(realName);
            p.setGender(gender);
            p.setBirthday(birthday);
            p.setBio(bio);
            userProfileMapper.insert(p);
        }
        log.info("用户{}更新个人资料（nickname={}, gender={}）", userId, nickname, gender);
        return Result.ok(buildProfileData(user, p), "保存成功");
    }

    /**
     * 上传头像
     * 策略：前端 >10MB 先压缩，后端兜底再次压缩（thumbnailator）。
     * 服务端处理：主图等比缩到最大边长 200（JPEG q0.8 起，循环降质至 <50KB）；
     *            另存 200x200 居中裁剪缩略图供列表展示。
     * 不保存原图，只落压缩后小图；URL 带 ?v= 时间戳，换头像后浏览器自然刷新。
     */
    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的头像");
        }
        // 后端再次校验大小：超过 20MB 直接拒绝
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return Result.fail("图片过大，请选择 20MB 以内的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("仅支持上传图片文件");
        }

        String ext = resolveExtension(file.getOriginalFilename(), contentType);
        if (!ALLOWED_EXT.contains(ext)) {
            return Result.fail("仅支持 jpg/jpeg/png/webp 格式");
        }

        try {
            // ImageIO.read 同时校验图片有效性（非图片返回 null）
            BufferedImage src = ImageIO.read(file.getInputStream());
            if (src == null) {
                return Result.fail("图片内容无效或已损坏，请重新选择");
            }

            // 主图压缩（thumbnailator，最大边长 200，JPEG q0.8 起，循环降质至 <50KB）
            byte[] mainBytes = compressAvatar(src);
            // 200x200 居中裁剪缩略图（同样 <50KB）
            byte[] thumbBytes = makeThumb(src);

            Path avatarDir = Paths.get(uploadDir, "avatar").toAbsolutePath();
            Files.createDirectories(avatarDir);
            long baseId = idGenerator.nextId();
            String mainName = userId + "_" + baseId + ".jpg";
            String thumbName = userId + "_" + baseId + "_thumb.jpg";
            Files.write(avatarDir.resolve(mainName), mainBytes);
            Files.write(avatarDir.resolve(thumbName), thumbBytes);

            // 返回地址带 ?v= 版本号：文件名虽每次不同（雪花 ID），但附加时间戳
            // 可彻底规避浏览器/CDN/中间代理对 /uploads/avatar/ 路径的缓存命中，
            // 保证前端拿到新 URL 后必定发起新请求，不会展示旧头像。
            String version = String.valueOf(System.currentTimeMillis());
            String avatarUrl = "/uploads/avatar/" + mainName + "?v=" + version;
            String avatarThumbUrl = "/uploads/avatar/" + thumbName + "?v=" + version;

            // 头像地址立即落库：已有资料行只更新头像，无行则创建默认资料行
            Optional<UserProfile> existed = userProfileMapper.findByUserId(userId);
            if (existed.isPresent()) {
                userProfileMapper.updateAvatar(userId, avatarUrl, avatarThumbUrl);
            } else {
                UserProfile p = new UserProfile();
                p.setUserId(userId);
                p.setAvatar(avatarUrl);
                p.setAvatarThumb(avatarThumbUrl);
                p.setGender("UNKNOWN");
                userProfileMapper.insert(p);
            }
            log.info("用户{}上传头像：{}（原图{}B -> 主图{}B，缩略图{}B）",
                    userId, avatarUrl, file.getSize(), mainBytes.length, thumbBytes.length);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("avatar", avatarUrl);
            data.put("avatarThumb", avatarThumbUrl);
            return Result.ok(data, "头像上传成功");
        } catch (IOException e) {
            log.error("用户{}头像上传/压缩失败：{}", userId, e.getMessage(), e);
            return Result.fail("头像上传失败，请重新上传");
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 头像主图压缩（thumbnailator）：等比缩放到最大边 200，转 JPEG。
     * 起始质量 0.8，若超过 50KB 则循环降质量（每次 -0.1，下限 0.3）直至达标。
     * 不保存原图，只落压缩后的小图。
     */
    private byte[] compressAvatar(BufferedImage src) throws IOException {
        float q = JPEG_QUALITY_START;
        byte[] bytes = encodeAvatarJpeg(src, q, false);
        while (bytes.length > AVATAR_TARGET_BYTES && q > 0.3f) {
            q -= 0.1f;
            bytes = encodeAvatarJpeg(src, q, false);
        }
        return bytes;
    }

    /**
     * 头像缩略图（thumbnailator）：从原图居中取正方形区域（短边），
     * 再缩放到 200x200，转 JPEG，保证比例不变形。
     * 同样循环降质量至 <50KB。
     */
    private byte[] makeThumb(BufferedImage src) throws IOException {
        float q = JPEG_QUALITY_START;
        byte[] bytes = encodeAvatarJpeg(src, q, true);
        while (bytes.length > AVATAR_TARGET_BYTES && q > 0.3f) {
            q -= 0.1f;
            bytes = encodeAvatarJpeg(src, q, true);
        }
        return bytes;
    }

    /**
     * 用 thumbnailator 编码头像 JPEG。
     * thumb=true：居中裁剪正方形后缩放到 200x200（缩略图，列表展示用）。
     * thumb=false：等比缩放使最大边 ≤ 200（主图，详情/编辑页用）。
     */
    private byte[] encodeAvatarJpeg(BufferedImage src, float quality, boolean thumb) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Thumbnails.Builder<BufferedImage> b = Thumbnails.of(src)
                .outputQuality(quality)
                .outputFormat("jpg");
        if (thumb) {
            int min = Math.min(src.getWidth(), src.getHeight());
            b.sourceRegion(Positions.CENTER, min, min).size(THUMB_EDGE, THUMB_EDGE);
        } else {
            b.size(MAX_AVATAR_EDGE, MAX_AVATAR_EDGE);
        }
        b.toOutputStream(os);
        return os.toByteArray();
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

    /** 构建前端资料契约（无资料行时给出默认值） */
    private Map<String, Object> buildProfileData(User user, UserProfile p) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("phone", user.getPhone());
        data.put("avatar", p == null ? null : p.getAvatar());
        data.put("avatarThumb", p == null ? null : p.getAvatarThumb());
        data.put("nickname", p == null ? null : p.getNickname());
        data.put("realName", p == null ? null : p.getRealName());
        data.put("gender", p == null ? "UNKNOWN" : p.getGender());
        data.put("birthday", p == null ? null : p.getBirthday());
        data.put("bio", p == null ? null : p.getBio());
        data.put("createTime", user.getCreatedAt());
        return data;
    }

    /** 去空白：空白字符串归一为 null */
    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        // 前端未传字段时 Map 中可能出现字面量 "null"
        if (t.isEmpty() || "null".equalsIgnoreCase(t) || "undefined".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    /** 从原文件名取扩展名，非法时按 contentType 兜底 */
    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                    .trim().toLowerCase();
            if (!ext.isEmpty()) return ext;
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

}
