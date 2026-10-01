package com.xiaoshan.fitness.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员卡有效期 / 到期延迟 统一策略（生产口径）。
 * <p>
 * 有效期严格按卡类型 {@code duration_days} 天计算：
 * <ul>
 *   <li>激活时 start_time = now，end_time = now + duration_days 天；</li>
 *   <li>普通卡与按节数计费的私教课卡一视同仁，有效期都取 duration_days。</li>
 * </ul>
 * 延迟消息毫秒数一律由 {@code end_time - now} 动态计算，不写死 7 天/1 天的毫秒常量：
 * <ul>
 *   <li>到期前 7 天提醒：delay = end_time - now - 7 天，剩余不足 7 天（无提前量）不发；</li>
 *   <li>到期前 1 天提醒：delay = end_time - now - 1 天，剩余不足 1 天不发；</li>
 *   <li>到期消息：delay = end_time - now，始终发送。</li>
 * </ul>
 * <p>
 * <b>延迟上限保护</b>：x-delayed-message 插件旧版本按 32 位有符号整数处理 x-delay
 * （上限约 24.8 天），年卡等长期卡的原始延迟（365 天 ≈ 3.15×10¹⁰ms）超出后可能被
 * 提前/立即投递。因此实际发送的延迟一律经 {@link #cappedDelayMs} 截断到
 * {@link #MAX_SAFE_DELAY_MS}；被截断的消息会提前到达消费端，由消费端的
 * "提醒窗口校验"跳过（到期消息则因未到期跳过），最终由定时扫描任务
 * （MembershipExpireScanTask）在到期/提醒窗口期兜底触发。
 */
@Component
public class MembershipExpirePolicy {

    /** 到期前提醒天数（提前 7 天 / 提前 1 天） */
    public static final int REMIND_BEFORE_DAYS_7 = 7;
    public static final int REMIND_BEFORE_DAYS_1 = 1;

    /**
     * x-delay 安全上限：32 位有符号整数毫秒（约 24.8 天）。
     * 超过该值的延迟会被截断，剩余部分依赖定时扫描任务兜底。
     */
    public static final long MAX_SAFE_DELAY_MS = Integer.MAX_VALUE;

    /**
     * 计算会员卡到期时间：end_time = now + durationDays 天。
     *
     * @param now          起点时间（激活时间）
     * @param durationDays 卡类型有效天数
     * @return 到期时间；durationDays 为 null 时返回 null（历史卡无法确定有效期）
     */
    public LocalDateTime endTime(LocalDateTime now, Integer durationDays) {
        return durationDays != null ? now.plusDays(durationDays) : null;
    }

    /**
     * 到期消息延迟：end_time - now（始终为非负数）。
     */
    public long expireDelayMs(LocalDateTime now, LocalDateTime endTime) {
        return Math.max(Duration.between(now, endTime).toMillis(), 0L);
    }

    /**
     * 计算"到期前 N 天提醒"的延迟毫秒数。
     * <p>
     * delay = (end_time - now) - N 天；当剩余有效期不足 N 天（delay &le; 0，
     * 即没有提前量）时返回 null，调用方不调度该提醒。
     *
     * @param now              激活时刻
     * @param endTime          会员卡到期时刻
     * @param remindBeforeDays 提前提醒天数（7 或 1）
     * @return 延迟毫秒数；不应调度时返回 null
     */
    public Long remindDelayMs(LocalDateTime now, LocalDateTime endTime, int remindBeforeDays) {
        if (endTime == null) {
            return null;
        }
        long delayMs = Duration.between(now, endTime).minusDays(remindBeforeDays).toMillis();
        return delayMs > 0 ? delayMs : null;
    }

    /** 当前有效期口径的可读描述（日志用） */
    public String describe() {
        return "正式模式：按卡类型 duration_days 天";
    }

    /**
     * 截断到插件可安全处理的延迟上限：负数归 0，超过 {@link #MAX_SAFE_DELAY_MS} 取上限。
     */
    public long cappedDelayMs(long delayMs) {
        return Math.min(Math.max(delayMs, 0L), MAX_SAFE_DELAY_MS);
    }
}
