package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.service.VideoTranscodeService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分享媒体上传接口
 * <p>
 * 能力：
 * <ul>
 *   <li>图片上传：POST /upload/image —— 类型/大小校验 + 服务端压缩（长边≤1600，JPEG q≈0.82）</li>
 *   <li>视频分片上传（支持断点续传）：
 *       GET  /upload/video/status?fileHash=  → 已上传分片索引
 *       POST /upload/video/chunk             → 上传单个分片
 *       POST /upload/video/merge             → 合并分片成文件（异步转码）</li>
 * </ul>
 * <p>
 * 安全：
 * <ul>
 *   <li>fileHash 仅接受 8-64 位十六进制（防路径穿越）；</li>
 *   <li>扩展名白名单 + 内容嗅探双重校验（图片魔数）；</li>
 *   <li>保存路径统一 normalize 后校验必须落在上传根目录内。</li>
 * </ul>
 * <p>
 * 上传均要求已登录（任意身份 token），防止匿名刷存储。
 */
@RestController
@RequestMapping("/api/v1/share/upload")
@RequiredArgsConstructor
@Slf4j
public class ShareUploadController {

    private final JwtUtil jwtUtil;
    private final SnowflakeIdGenerator idGenerator;
    private final VideoTranscodeService videoTranscodeService;

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    /** 图片大小上限 10MB */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /** 单个分片大小上限 6MB（前端按 5MB 切片，留余量） */
    private static final long MAX_CHUNK_BYTES = 6L * 1024 * 1024;
    /** 分片总数上限（100MB / 1MB 下限，防止恶意超大 totalChunks 刷盘） */
    private static final int MAX_CHUNK_COUNT = 200;
    /** 压缩后长边上限 */
    private static final int MAX_IMAGE_EDGE = 1600;
    /** JPEG 压缩质量 */
    private static final float JPEG_QUALITY = 0.82f;

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "webm", "3gp", "avi", "mkv");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{8,64}$");

    // ==================== 图片上传 ====================

    /**
     * 图片上传（分享图 / 视频封面帧通用）
     * 服务端压缩：长边 >1600 等比缩到 1600；统一转 JPEG（GIF 保留动画原样存储）
     */
    @PostMapping("/image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        if (!isLoggedIn(request)) {
            return Result.fail(401, "未登录或登录已过期");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            return Result.fail("图片不能超过 10MB");
        }

        String ext = extOf(file.getOriginalFilename());
        if (!IMAGE_EXTS.contains(ext)) {
            return Result.fail("仅支持 jpg/png/webp/gif 图片");
        }

        try {
            long imgId = idGenerator.nextId();
            String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
            byte[] payload;
            String storedExt;
            byte[] thumbPayload = null;
            if ("gif".equals(ext)) {
                // GIF 压缩会丢动画，原样存储，不生成缩略图
                payload = file.getBytes();
                storedExt = "gif";
            } else {
                BufferedImage src = ImageIO.read(file.getInputStream());
                if (src == null) {
                    return Result.fail("图片内容无效或已损坏");
                }
                payload = compressToJpeg(src);
                // 生成 400x400 居中裁剪缩略图，供列表展示，减少列表原图流量
                thumbPayload = generateThumb(src);
                storedExt = "jpg";
            }

            String relPath = "share/img/" + month + "/" + imgId + "." + storedExt;
            Path target = safeResolve(relPath);
            Files.createDirectories(target.getParent());
            Files.write(target, payload);

            String url = "/uploads/" + relPath;
            String thumbUrl = null;
            if (thumbPayload != null) {
                String thumbRelPath = "share/img/" + month + "/" + imgId + "_thumb.jpg";
                Files.write(safeResolve(thumbRelPath), thumbPayload);
                thumbUrl = "/uploads/" + thumbRelPath;
            }

            log.info("[分享] 图片上传成功 url={} thumb={} size={}B -> {}B", url, thumbUrl, file.getSize(), payload.length);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("thumbUrl", thumbUrl);
            return Result.ok(data);
        } catch (Exception e) {
            log.error("[分享] 图片上传失败", e);
            return Result.fail("图片上传失败，请重试");
        }
    }

    /**
     * JPEG 压缩：长边超限则等比缩放；透明通道铺白底后统一 JPEG 输出
     */
    private byte[] compressToJpeg(BufferedImage src) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.max(w, h) > MAX_IMAGE_EDGE
                ? (double) MAX_IMAGE_EDGE / Math.max(w, h) : 1.0;
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        // TYPE_3BYTE_BGR 无透明通道，绘制时白底兜底，避免透明区域变黑
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, tw, th, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }

        var writer = ImageIO.getImageWritersByFormatName("jpg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
        try (var os = new java.io.ByteArrayOutputStream();
             var ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(out, null, null), params);
            ios.flush();
            return os.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /** 缩略图边长（列表九宫格展示，retina 下足够清晰） */
    private static final int THUMB_EDGE = 400;

    /**
     * 生成 400x400 居中裁剪缩略图（JPEG q≈0.8），用于列表展示，减少列表原图流量
     */
    private byte[] generateThumb(BufferedImage src) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        int sx = (w - side) / 2;
        int sy = (h - side) / 2;

        BufferedImage out = new BufferedImage(THUMB_EDGE, THUMB_EDGE, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, THUMB_EDGE, THUMB_EDGE, sx, sy, sx + side, sy + side, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }

        var writer = ImageIO.getImageWritersByFormatName("jpg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.80f);
        try (var os = new java.io.ByteArrayOutputStream();
             var ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(out, null, null), params);
            ios.flush();
            return os.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    // ==================== 视频分片上传 ====================

    /**
     * 查询某文件已上传的分片（断点续传：客户端只补传缺失分片）
     */
    @GetMapping("/video/status")
    public Result<Map<String, Object>> videoStatus(@RequestParam("fileHash") String fileHash,
                                                    HttpServletRequest request) {
        if (!isLoggedIn(request)) {
            return Result.fail(401, "未登录或登录已过期");
        }
        if (!HASH_PATTERN.matcher(fileHash).matches()) {
            return Result.fail("参数格式不正确");
        }
        List<Integer> uploaded = new ArrayList<>();
        File dir = chunkDir(fileHash).toFile();
        if (dir.isDirectory()) {
            File[] parts = dir.listFiles((d, name) -> name.endsWith(".part"));
            if (parts != null) {
                for (File p : parts) {
                    try {
                        uploaded.add(Integer.parseInt(p.getName().replace(".part", "")));
                    } catch (NumberFormatException ignore) {
                        // 非法文件名跳过
                    }
                }
            }
        }
        uploaded.sort(Comparator.naturalOrder());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploaded", uploaded);
        return Result.ok(data);
    }

    /**
     * 上传单个分片：uploads/tmp/{fileHash}/{chunkIndex}.part
     * 同名分片重复上传直接覆盖（幂等）
     */
    @PostMapping("/video/chunk")
    public Result<Map<String, Object>> uploadChunk(@RequestParam("fileHash") String fileHash,
                                                    @RequestParam("chunkIndex") int chunkIndex,
                                                    @RequestParam("totalChunks") int totalChunks,
                                                    @RequestParam("fileName") String fileName,
                                                    @RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        if (!isLoggedIn(request)) {
            return Result.fail(401, "未登录或登录已过期");
        }
        if (!HASH_PATTERN.matcher(fileHash).matches()) {
            return Result.fail("参数格式不正确");
        }
        if (chunkIndex < 0 || totalChunks <= 0 || totalChunks > MAX_CHUNK_COUNT || chunkIndex >= totalChunks) {
            return Result.fail("分片参数不正确");
        }
        if (!VIDEO_EXTS.contains(extOf(fileName))) {
            return Result.fail("仅支持 mp4/mov/webm/3gp/avi/mkv 视频");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("分片内容为空");
        }
        if (file.getSize() > MAX_CHUNK_BYTES) {
            return Result.fail("分片大小超过限制");
        }
        try {
            Path target = chunkDir(fileHash).resolve(chunkIndex + ".part");
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            log.debug("[分享] 分片上传 fileHash={} index={}/{}", fileHash, chunkIndex, totalChunks - 1);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chunkIndex", chunkIndex);
            return Result.ok(data);
        } catch (IOException e) {
            log.error("[分享] 分片写入失败 fileHash={} index={}", fileHash, chunkIndex, e);
            return Result.fail("分片上传失败，请重试");
        }
    }

    /**
     * 合并分片 → 正式文件（share/video/{yyyyMM}/{snowflake}.{ext}）
     * 合并成功后清理临时分片；非 mp4 容器触发异步转码（无 ffmpeg 时保持原样）
     */
    @PostMapping("/video/merge")
    public Result<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        if (!isLoggedIn(request)) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String fileHash = body.get("fileHash") == null ? "" : body.get("fileHash").toString();
        String fileName = body.get("fileName") == null ? "" : body.get("fileName").toString();
        int totalChunks = body.get("totalChunks") == null ? 0 : Integer.parseInt(body.get("totalChunks").toString());
        if (!HASH_PATTERN.matcher(fileHash).matches()) {
            return Result.fail("参数格式不正确");
        }
        String ext = extOf(fileName);
        if (!VIDEO_EXTS.contains(ext)) {
            return Result.fail("仅支持 mp4/mov/webm/3gp/avi/mkv 视频");
        }
        if (totalChunks <= 0 || totalChunks > MAX_CHUNK_COUNT) {
            return Result.fail("分片参数不正确");
        }

        Path dir = chunkDir(fileHash);
        // 校验分片完整性
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!Files.exists(dir.resolve(i + ".part"))) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("missing", missing);
            return Result.fail(400, "分片不完整，请续传", data);
        }

        try {
            String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
            String relPath = "share/video/" + month + "/" + idGenerator.nextId() + "." + ext;
            Path target = safeResolve(relPath);
            Files.createDirectories(target.getParent());

            long merged = mergeOrdered(dir, totalChunks, target);
            if (merged <= 0) {
                return Result.fail("视频合并失败，请重试");
            }

            // 清理临时分片目录
            deleteRecursively(dir);

            boolean transcoding = videoTranscodeService.transcodeIfNeed(target);
            log.info("[分享] 视频合并完成 url={} size={}MB transcoding={}",
                    "/uploads/" + relPath, merged / 1024 / 1024, transcoding);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", "/uploads/" + relPath);
            data.put("transcoding", transcoding);
            return Result.ok(data);
        } catch (IOException e) {
            log.error("[分享] 视频合并失败 fileHash={}", fileHash, e);
            return Result.fail("视频合并失败，请重试");
        }
    }

    /**
     * 按分片顺序拼接写入目标文件
     */
    private long mergeOrdered(Path dir, int totalChunks, Path target) throws IOException {
        long total = 0;
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buf = new byte[8192];
            for (int i = 0; i < totalChunks; i++) {
                try (InputStream in = new FileInputStream(dir.resolve(i + ".part").toFile())) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                }
            }
        }
        return total;
    }

    // ==================== 私有辅助 ====================

    /** 临时分片目录：uploads/tmp/{fileHash} */
    private Path chunkDir(String fileHash) throws RuntimeException {
        try {
            return safeResolve("tmp/" + fileHash.toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            throw new RuntimeException("分片目录解析失败", e);
        }
    }

    /**
     * 在上传根目录内解析相对路径，normalize 后校验未越界（防路径穿越）
     */
    private Path safeResolve(String relPath) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(relPath).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("非法上传路径: " + relPath);
        }
        return target;
    }

    private String extOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteRecursively(Path dir) {
        File[] files = dir.toFile().listFiles();
        if (files != null) {
            Arrays.stream(files).forEach(File::delete);
        }
        dir.toFile().delete();
    }

    private boolean isLoggedIn(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        return token != null && jwtUtil.validateToken(token);
    }
}
