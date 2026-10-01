package com.xiaoshan.fitness.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 人脸识别配置（face.mode 切换 Mock / 百度 AI / SmartJavaAI 离线）。
 * <p>
 * mode=mock（缺省）：本地图片平均哈希比对，dev 兜底，无需密钥；
 * mode=baidu：百度智能云人脸识别 V3，需配置 API Key / Secret Key（建议走环境变量）；
 * mode=smartjava：SmartJavaAI 离线人脸识别，模型由 DJL 自动下载，无需手动配置模型路径。
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "face")
public class FaceProperties {

    /** 识别服务模式：mock / baidu / smartjava */
    private String mode = "mock";

    /** 百度 AI 人脸识别配置 */
    private Baidu baidu = new Baidu();

    /** SmartJavaAI 离线人脸识别配置 */
    private SmartJava smartjava = new SmartJava();

    @PostConstruct
    void validate() {
        String m = mode == null ? "" : mode.trim();
        if (!"mock".equals(m) && !"baidu".equals(m) && !"smartjava".equals(m)) {
            throw new IllegalStateException(
                    "face.mode 配置非法：" + mode + "，仅支持 mock / baidu / smartjava");
        }
        if ("baidu".equals(m)) {
            if (baidu.getApiKey() == null || baidu.getApiKey().isBlank()
                    || baidu.getSecretKey() == null || baidu.getSecretKey().isBlank()) {
                throw new IllegalStateException(
                        "face.mode=baidu 时必须配置百度 AI 密钥：face.baidu.api-key / face.baidu.secret-key"
                                + "（或环境变量 BAIDU_FACE_API_KEY / BAIDU_FACE_SECRET_KEY）");
            }
        }
        log.info("人脸识别服务模式：{}", m);
    }

    @Data
    public static class Baidu {

        /** 百度智能云应用 API Key（建议通过环境变量 BAIDU_FACE_API_KEY 注入） */
        private String apiKey = "";

        /** 百度智能云应用 Secret Key（建议通过环境变量 BAIDU_FACE_SECRET_KEY 注入） */
        private String secretKey = "";

        /** 百度人脸库分组 ID（groupId 不存在时首次注册自动创建） */
        private String groupId = "fitness_users";

        /** 人脸搜索相似度阈值（0-100），score ≥ 此值才视为匹配，默认 80 */
        private Integer scoreThreshold = 80;

        /**
         * 图片质量控制：NONE / LOW / NORMAL / HIGH（默认 NORMAL）。
         * 演示机摄像头逆光模糊导致录入/核销受限时，可降级为 LOW 或 NONE。
         */
        private String qualityControl = "NORMAL";

        /** 在线活体控制：NONE / NORMAL / STRICT（默认 NORMAL） */
        private String livenessControl = "NORMAL";

        /** 连接百度接口超时（毫秒） */
        private int connectTimeoutMs = 3000;

        /** 读取百度接口超时（毫秒） */
        private int readTimeoutMs = 10000;
    }

    @Data
    public static class SmartJava {

        /** 人脸搜索相似度阈值（0-1），默认 0.62 */
        private double similarityThreshold = 0.62;

        /** 模型缓存目录（DJL 自动下载模型存放位置） */
        private String modelCachePath = "data/ai-models";
    }
}
