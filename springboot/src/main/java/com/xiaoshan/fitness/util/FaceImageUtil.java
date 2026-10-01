package com.xiaoshan.fitness.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * 图像平均哈希（aHash）工具：开发环境 Mock 人脸比对的代理信号。
 * <p>
 * 将图片缩放为 8×8 灰度，按均值二值化得到 64 位哈希；通过 Hamming 距离衡量两图相似度：
 * 同一人在不同帧下距离通常较小（< 12），不同人距离较大。
 * <p>
 * 仅用于开发环境 Mock 比对，不依赖任何人脸 SDK；
 * 生产环境由 BaiduFaceService 通过百度 AI 人脸搜索接口实现真实比对。
 */
public final class FaceImageUtil {

    /** 比对阈值：Hamming 距离 <= 此值视为同一个人 */
    public static final int MATCH_THRESHOLD = 12;

    private FaceImageUtil() {
    }

    /**
     * 计算图片的 64 位平均哈希。
     *
     * @param imageBase64 图片 Base64（可含 data:image/...;base64, 前缀）
     * @return 64 位哈希；图片为空或无法解码返回 null
     */
    public static Long averageHash(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return null;
        }
        byte[] bytes = decodeBase64(imageBase64);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) {
                return null; // 非 ImageIO 支持的图片格式
            }
            BufferedImage gray = to8x8Gray(img);
            return computeHash(gray);
        } catch (Exception e) {
            return null;
        }
    }

    /** Hamming 距离（两哈希异或后的置位位数） */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static byte[] decodeBase64(String s) {
        int i = s.indexOf(",");
        String raw = (i >= 0 && s.substring(0, i).contains("base64")) ? s.substring(i + 1) : s;
        try {
            return Base64.getDecoder().decode(raw.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BufferedImage to8x8Gray(BufferedImage src) {
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, 8, 8, null);
        g.dispose();
        return small;
    }

    private static long computeHash(BufferedImage gray8x8) {
        int[] px = new int[64];
        gray8x8.getRGB(0, 0, 8, 8, px, 0, 8);
        long sum = 0;
        for (int p : px) {
            // 灰度图三通道相等，取红通道
            sum += (p >> 16) & 0xFF;
        }
        long avg = sum / 64;
        long hash = 0;
        for (int k = 0; k < 64; k++) {
            int v = (px[k] >> 16) & 0xFF;
            if (v > avg) {
                hash |= (1L << k);
            }
        }
        return hash;
    }
}
