package com.xiaoshan.fitness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * scanBasePackages 显式指定扫描包，确保 controller/service/mapper 等被扫描
 * <p>
 * EnableScheduling：启用定时任务（在线状态 Redis TTL 周期续期等）
 */
@SpringBootApplication(scanBasePackages = "com.xiaoshan.fitness")
@EnableScheduling
public class FitnessApplication {
    public static void main(String[] args) {
        SpringApplication.run(FitnessApplication.class, args);
    }
}
