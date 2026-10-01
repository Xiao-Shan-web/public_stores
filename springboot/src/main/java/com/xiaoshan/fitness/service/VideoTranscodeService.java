package com.xiaoshan.fitness.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频转码服务（可选 ffmpeg）
 * <p>
 * 策略：
 * <ul>
 *   <li>启动/首次调用时探测 ffmpeg（配置 app.share.ffmpeg-path，缺省在 PATH 中找）；</li>
 *   <li>不可用 → 直接返回 false，上传的视频原样播放
 *       （浏览器/WebView 对 mp4(H.264) 原生支持，mov 等容器在部分端可能无法播放）；</li>
 *   <li>可用 → 对非 mp4 文件异步转码为 H.264 + AAC + faststart 的 mp4：
 *       先写 {name}.transcode.mp4.tmp，成功后原子替换原文件，失败保留原文件；</li>
 *   <li>单线程执行器排队转码，避免多个大视频并发挤占 CPU；应用关闭时等待在途任务。</li>
 * </ul>
 * <p>
 * 说明：服务器安装 ffmpeg 后无需改代码，重启即获得转码能力。
 * Windows 安装：https://www.gyan.dev/ffmpeg/builds/ 下载后将 bin 加入 PATH 或配置绝对路径。
 */
@Slf4j
@Service
public class VideoTranscodeService {

    @Value("${app.share.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** 转码超时（分钟）：100MB 视频在普通服务器 veryfast 档一般 < 10 分钟 */
    private static final long TIMEOUT_MINUTES = 30;

    /** ffmpeg 可用性：null=未探测，true/false=探测结果 */
    private volatile Boolean ffmpegAvailable;

    /** 单线程转码执行器（守护线程，队列排队执行） */
    private final ExecutorService transcodeExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "video-transcode-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 判断是否需要转码并提交异步任务。
     *
     * @return true = 已提交异步转码；false = 无需/无法转码（原样播放）
     */
    public boolean transcodeIfNeed(Path videoFile) {
        if (videoFile == null || !Files.exists(videoFile)) {
            return false;
        }
        String ext = extOf(videoFile.getFileName().toString());
        // mp4 容器大概率已是 H.264（前端截帧方案输出/常见拍摄格式），不重复转码省 CPU；
        // 其余容器（mov/webm/avi/mkv/3gp）统一转 mp4 保证全端可播
        if ("mp4".equals(ext) || "gif".equals(ext)) {
            return false;
        }
        if (!isFfmpegAvailable()) {
            log.warn("[转码] 未检测到 ffmpeg，视频按原样播放（建议安装 ffmpeg 以保证全端可播）：{}",
                    videoFile.getFileName());
            return false;
        }
        transcodeExecutor.submit(() -> doTranscode(videoFile));
        return true;
    }

    /**
     * 同步执行转码并原子替换。失败时保留原文件，绝不弄丢已上传内容。
     */
    private void doTranscode(Path src) {
        Path tmp = src.resolveSibling(src.getFileName() + ".transcode.mp4.tmp");
        try {
            log.info("[转码] 开始：{} -> mp4", src.getFileName());
            Process proc = new ProcessBuilder(
                    ffmpegPath, "-y",
                    "-i", src.toAbsolutePath().toString(),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
                    "-movflags", "+faststart",
                    "-c:a", "aac", "-b:a", "128k",
                    tmp.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            // ffmpeg 进度输出到 stdout，读取避免缓冲区塞满阻塞，但不逐行记录
            byte[] buf = new byte[4096];
            try (var in = proc.getInputStream()) {
                while (in.read(buf) > 0) {
                    // drain
                }
            }
            boolean finished = proc.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                log.error("[转码] 超时（{}分钟），保留原文件：{}", TIMEOUT_MINUTES, src.getFileName());
                Files.deleteIfExists(tmp);
                return;
            }
            if (proc.exitValue() != 0 || !Files.exists(tmp) || Files.size(tmp) == 0) {
                log.error("[转码] ffmpeg 退出码 {}，保留原文件：{}", proc.exitValue(), src.getFileName());
                Files.deleteIfExists(tmp);
                return;
            }
            // 原子替换：转码成功后覆盖原视频
            Files.move(tmp, src, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("[转码] 完成：{} 已替换为 H.264 mp4（{}MB）",
                    src.getFileName(), Files.size(src) / 1024 / 1024);
        } catch (Exception e) {
            log.error("[转码] 失败，保留原文件：{}", src.getFileName(), e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // 清理失败不影响主流程
            }
        }
    }

    /**
     * 探测 ffmpeg（结果缓存，进程生命周期内只探测一次）
     */
    private boolean isFfmpegAvailable() {
        Boolean cached = ffmpegAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (ffmpegAvailable != null) {
                return ffmpegAvailable;
            }
            try {
                Process proc = new ProcessBuilder(ffmpegPath, "-version")
                        .redirectErrorStream(true)
                        .start();
                boolean done = proc.waitFor(Duration.ofSeconds(5).toSeconds(), TimeUnit.SECONDS);
                if (!done) {
                    proc.destroyForcibly();
                }
                ffmpegAvailable = done && proc.exitValue() == 0;
            } catch (Exception e) {
                ffmpegAvailable = false;
            }
            log.info("[转码] ffmpeg 探测结果：available={} (path={})", ffmpegAvailable, ffmpegPath);
            return ffmpegAvailable;
        }
    }

    private String extOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    public void shutdown() {
        transcodeExecutor.shutdown();
        try {
            if (!transcodeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                transcodeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
