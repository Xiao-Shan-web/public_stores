package com.xiaoshan.fitness.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 优惠券表结构补丁器（启动期幂等执行）
 * <p>
 * 背景：折扣券（DISCOUNT）需要 coupons.discount / coupons.max_discount 两列，
 * 老库升级时若忘记执行 schema_full.sql 中的迁移段，所有券查询都会因缺列直接报错。
 * 这里在启动时检查 information_schema，缺列才补，已存在则跳过（幂等、可重复启动）。
 * <p>
 * 失败只记警告不影响启动：列确实缺失时会由具体查询报错暴露，便于排查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponSchemaPatcher implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final String TABLE = "coupons";

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumnIfMissing("discount",
                    "DECIMAL(4,2) DEFAULT NULL COMMENT '折扣率（DISCOUNT：0.85 表示 85 折）' AFTER `amount`");
            addColumnIfMissing("max_discount",
                    "DECIMAL(10,2) DEFAULT NULL COMMENT '最高优惠上限（DISCOUNT，NULL 不封顶）' AFTER `discount`");
        } catch (Exception e) {
            log.warn("优惠券折扣列检查失败（可手动执行 schema_full.sql 迁移段）：{}", e.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String definition) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, TABLE, column);
        if (cnt != null && cnt > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` ADD COLUMN `" + column + "` " + definition);
        log.info("优惠券表已自动补列：coupons.{}", column);
    }
}
