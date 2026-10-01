package com.xiaoshan.fitness.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 时间格式配置。
 * <p>
 * 统一把 {@link java.time.LocalDateTime} 序列化为 {@code yyyy-MM-dd HH:mm:ss}（不带 T），
 * 反序列化兼容同一格式。列表页通常截取到分钟（yyyy-MM-dd HH:mm），详情页展示完整秒。
 * <p>
 * Spring Boot 4 默认使用 Jackson 3（tools.jackson.*）驱动 HTTP 消息转换，
 * 不再自动装配 Jackson 2 的 {@code com.fasterxml.jackson.databind.ObjectMapper}。
 * 业务代码（CacheService 缓存序列化、ShareController 图片列表存储）显式依赖该 Bean，
 * 这里基于同一个 Builder 显式构建，保持 LocalDateTime 格式口径一致。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return Jackson2ObjectMapperBuilder.json()
                .serializers(new LocalDateTimeSerializer(formatter))
                .deserializers(new LocalDateTimeDeserializer(formatter));
    }

    /**
     * 显式提供 Jackson 2 ObjectMapper（Boot 4 起不再自动装配）：
     * 供缓存 JSON 序列化、images 列表存取等业务代码注入使用
     */
    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper(
            Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
