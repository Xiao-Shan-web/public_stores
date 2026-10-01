package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.config.RabbitMQConfig;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员卡到期定时扫描兜底任务。
 * <p>
 * 背景：x-delayed-message 插件旧版本按 32 位整数处理 x-delay（上限约 24.8 天），
 * 年卡等长期卡的到期/提醒消息会被生产端截断，截断后提前到达的消息被
 * {@link MembershipExpireHandler} 的窗口校验跳过。本任务定时扫描数据库，
 * 在到期时刻 / 提醒窗口期兜底触发，同时补偿消息丢失、服务重启等异常。
 * <p>
 * 处理逻辑复用 {@link MembershipExpireHandler}，全程幂等：
 * 提醒按标题去重（messages.countReminder），过期为条件更新（ACTIVE→EXPIRED），
 * 与消息消费并发执行安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipExpireScanTask {

    /** 扫描间隔：30 分钟（到期/提醒的触发误差即 ±30 分钟，通知类场景可接受） */
    private static final long SWEEP_INTERVAL_MS = 30 * 60 * 1000L;

    private final MembershipMapper membershipMapper;
    private final MembershipExpireHandler membershipExpireHandler;

    /**
     * 兜底扫描：
     * <ol>
     *   <li>已到期的 ACTIVE 卡 → 标记过期 + 过期通知；</li>
     *   <li>7 天内将到期的 ACTIVE 卡 → 补发 7 天提醒（未发过时）；</li>
     *   <li>1 天内将到期的 ACTIVE 卡 → 补发 1 天提醒（未发过时）。</li>
     * </ol>
     */
    @Scheduled(fixedRate = SWEEP_INTERVAL_MS, initialDelay = 60 * 1000L)
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0, reminded7 = 0, reminded1 = 0;
        try {
            // 1. 到期兜底
            List<Long> expiredIds = membershipMapper.findActiveExpiredIds(now);
            for (Long id : expiredIds) {
                if (safeHandle(id, RabbitMQConfig.KIND_EXPIRE)) {
                    expired++;
                }
            }

            // 2. 提醒兜底（Handler 内按标题去重，重复调用安全）
            List<Long> expiring7 = membershipMapper.findActiveIdsExpiringBetween(now, now.plusDays(7));
            for (Long id : expiring7) {
                if (safeHandle(id, RabbitMQConfig.KIND_REMIND_7)) {
                    reminded7++;
                }
            }
            List<Long> expiring1 = membershipMapper.findActiveIdsExpiringBetween(now, now.plusDays(1));
            for (Long id : expiring1) {
                if (safeHandle(id, RabbitMQConfig.KIND_REMIND_1)) {
                    reminded1++;
                }
            }

            if (expired + reminded7 + reminded1 > 0) {
                log.info("会员卡到期兜底扫描完成：处理过期={}，7天提醒={}，1天提醒={}",
                        expired, reminded7, reminded1);
            }
        } catch (Exception e) {
            // 查询级异常仅记日志，等下一轮重试（处理幂等，重试安全）
            log.error("会员卡到期兜底扫描异常：expired={}, reminded7={}, reminded1={}",
                    expired, reminded7, reminded1, e);
        }
    }

    /** 单卡处理：异常只记日志不中断整轮扫描，等下一轮重试 */
    private boolean safeHandle(Long membershipId, String kind) {
        try {
            membershipExpireHandler.handle(membershipId, null, kind);
            return true;
        } catch (Exception e) {
            log.error("兜底扫描处理会员卡{}失败（kind={}），等下一轮重试", membershipId, kind, e);
            return false;
        }
    }
}
