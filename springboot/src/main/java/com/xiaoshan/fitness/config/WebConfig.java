package com.xiaoshan.fitness.config;

import com.xiaoshan.fitness.interceptor.AdminAuditInterceptor;
import com.xiaoshan.fitness.interceptor.IdempotentInterceptor;
import com.xiaoshan.fitness.interceptor.RequestLogInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置：文件上传静态资源映射 + 启动时创建上传目录 + 请求日志/幂等拦截器
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    private final RequestLogInterceptor requestLogInterceptor;
    private final IdempotentInterceptor idempotentInterceptor;
    private final AdminAuditInterceptor adminAuditInterceptor;

    /**
     * 启动时创建文件上传根目录
     */
    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir).toAbsolutePath();
            if (Files.notExists(path)) {
                Files.createDirectories(path);
                log.info("文件上传目录已创建: {}", path);
            } else {
                log.info("文件上传目录已存在: {}", path);
            }
        } catch (IOException e) {
            log.error("创建文件上传目录失败: {}", e.getMessage());
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/** URL 映射到物理文件上传目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + Paths.get(uploadDir).toAbsolutePath() + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求日志拦截器：拦截所有 /api/** 路径，排除静态资源
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/uploads/**",
                        "/ws/**"
                );
        // 幂等拦截器：匹配带 @Idempotent 注解的 Controller 方法
        // 执行顺序在 RequestLogInterceptor 之后（日志先行，便于追溯被拦截的请求）
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/uploads/**",
                        "/ws/**"
                );
        // 管理员操作审计：仅管理端写操作由拦截器内部判断，GET 与登录接口不记录
        registry.addInterceptor(adminAuditInterceptor)
                .addPathPatterns("/api/v1/admin/**")
                .excludePathPatterns(
                        "/api/v1/admin/login",
                        "/uploads/**",
                        "/ws/**"
                );
    }

}
