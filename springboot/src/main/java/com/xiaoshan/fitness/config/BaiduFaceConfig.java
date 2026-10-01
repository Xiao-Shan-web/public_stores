package com.xiaoshan.fitness.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 百度 AI 人脸识别 HTTP 客户端配置（仅 face.mode=baidu 时生效）。
 * <p>
 * 直接用 RestClient.builder() 构建：Framework 7 默认转换器集会自动注册容器内可用的
 * Jackson JSON 转换器；仅定制超时与百度网关基地址。不引入百度官方 SDK，
 * 避免老旧依赖与 Boot4 冲突。
 */
@Configuration
@ConditionalOnProperty(prefix = "face", name = "mode", havingValue = "baidu")
public class BaiduFaceConfig {

    public static final String BAIDU_BASE_URL = "https://aip.baidubce.com";

    @Bean
    public RestClient baiduFaceRestClient(FaceProperties faceProperties) {
        FaceProperties.Baidu cfg = faceProperties.getBaidu();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(BAIDU_BASE_URL)
                .requestFactory(factory)
                .build();
    }
}
