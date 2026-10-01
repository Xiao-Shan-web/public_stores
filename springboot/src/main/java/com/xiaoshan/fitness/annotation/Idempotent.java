package com.xiaoshan.fitness.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解
 * <p>
 * 标注在 Controller 方法上，由 {@code IdempotentInterceptor} 拦截。
 * <p>
 * 工作原理：
 * 在 preHandle 阶段以 "idempotent:{userId}:{httpMethod}:{uri}" 为 key
 * 执行 Redis SETNX（带 TTL），成功则放行；失败（key 已存在）则直接返回 429
 * "操作过于频繁，请稍后重试"，不再进入 Controller。
 * <p>
 * 适用场景：防快速重复提交（如连点支付/激活/核销按钮）。
 * 不适用于业务幂等（如同一订单重复支付），业务幂等应由 DB 唯一约束/状态校验兜底。
 * <p>
 * key 维度说明：仅以用户 + 接口为维度，不参与请求体哈希，避免读流。
 * 即同一用户对同一接口在 TTL 内只能调用一次，与参数无关。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等窗口期（秒），窗口内重复请求会被拦截。
     * 默认 5 秒，覆盖大部分双击场景；支付类建议 10-30 秒。
     */
    int expireSeconds() default 5;
}
