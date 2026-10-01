package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.SmsRecord;
import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.mapper.SmsRecordMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 关键通知短信服务
 * <p>
 * 覆盖三类关键节点：支付成功 {@link #notifyPaySuccess}、核销成功 {@link #notifyEntrySuccess}、
 * 会员卡激活成功 {@link #notifyActivateSuccess}。
 * <p>
 * 设计原则：
 * <ol>
 *   <li><b>不阻塞主链路</b>：调用方仅做一次异步提交，短信发送（含查库/限频/留痕）在线程池执行；
 *       任何异常都在本服务内消化，绝不向支付/核销/激活链路抛出。</li>
 *   <li><b>限频防骚扰</b>：同一用户同一模板在 {@code sms.rate-limit-seconds}（默认 60s）内只发一次。</li>
 *   <li><b>通道可切换</b>：{@code sms.channel=log}（默认，写日志+落库，开发/无凭证环境可直接演示）；
 *       {@code aliyun}（真实阿里云短信，需配置 AK/签名/模板，未配置时自动降级 LOG 并告警）。</li>
 *   <li><b>全程留痕</b>：成功/失败/跳过均写 sms_records（手机号脱敏），用于审计与对账。</li>
 *   <li><b>总开关</b>：{@code sms.enabled=false} 时直接丢弃，不发送也不留痕。</li>
 * </ol>
 */
@Slf4j
@Service
public class SmsService {

    /** 模板类型 */
    public static final String TPL_PAY_SUCCESS = "PAY_SUCCESS";
    public static final String TPL_ENTRY_SUCCESS = "ENTRY_SUCCESS";
    public static final String TPL_ACTIVATE_SUCCESS = "ACTIVATE_SUCCESS";

    /** 通道 */
    private static final String CHANNEL_LOG = "LOG";
    private static final String CHANNEL_ALIYUN = "ALIYUN";

    /** 发送状态 */
    private static final String ST_SUCCESS = "SUCCESS";
    private static final String ST_FAILED = "FAILED";
    private static final String ST_SKIPPED = "SKIPPED";

    /**
     * 通知模板。占位符使用 {key}，由 params 替换。
     * 文案控制在 70 字以内（单条短信长度），品牌统一为【山达健身】。
     */
    private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();
    static {
        TEMPLATES.put(TPL_PAY_SUCCESS,
                "【山达健身】您尾号{bizId}的订单支付成功，实付{amount}元，购买{cardName}，会员卡请在「到店」或「我的-会员卡」中激活。");
        TEMPLATES.put(TPL_ENTRY_SUCCESS,
                "【山达健身】{time}到店核销成功，{cardName}{timesPart}。如有疑问请联系门店。");
        TEMPLATES.put(TPL_ACTIVATE_SUCCESS,
                "【山达健身】您的{cardName}已激活成功，有效期至{endTime}，祝您训练愉快。");
    }

    @Value("${sms.enabled:true}")
    private boolean enabled;
    @Value("${sms.channel:log}")
    private String channel;
    @Value("${sms.rate-limit-seconds:60}")
    private long rateLimitSeconds;
    /** 阿里云短信（可选配置；channel=aliyun 但以下任一为空时自动降级 LOG） */
    @Value("${sms.aliyun.access-key-id:}")
    private String aliyunAccessKeyId;
    @Value("${sms.aliyun.access-key-secret:}")
    private String aliyunAccessKeySecret;
    @Value("${sms.aliyun.sign-name:山达健身}")
    private String aliyunSignName;

    private final UserMapper userMapper;
    private final SmsRecordMapper smsRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 短信专用线程池：2 核心线程，有界队列 500，拒绝策略 CallerRuns（极端高峰回退为调用线程执行，
     * 因 doSend 内部已全量兜底，不会拖垮主流程）。守护线程，不阻止 JVM 退出。
     */
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            r -> {
                Thread t = new Thread(r, "sms-sender");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    public SmsService(UserMapper userMapper, SmsRecordMapper smsRecordMapper,
                      RedisTemplate<String, Object> redisTemplate, SnowflakeIdGenerator idGenerator) {
        this.userMapper = userMapper;
        this.smsRecordMapper = smsRecordMapper;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
    }

    // ==================== 业务便捷入口 ====================

    /** 支付成功通知 */
    public void notifyPaySuccess(Long userId, String orderNo, String amount, String cardName) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("bizId", orderNo == null ? "" : tail(orderNo, 4));
        p.put("amount", amount);
        p.put("cardName", cardName);
        sendNotice(userId, TPL_PAY_SUCCESS, p, orderNo);
    }

    /** 核销成功通知（timesPart：次卡传"，剩余 N 次"，天卡传空串） */
    public void notifyEntrySuccess(Long userId, String cardName, String timesPart, String bizId) {
        Map<String, String> p = new LinkedHashMap<>();
        java.time.LocalTime now = java.time.LocalTime.now();
        p.put("time", String.format("%02d:%02d", now.getHour(), now.getMinute()));
        p.put("cardName", cardName);
        p.put("timesPart", timesPart == null ? "" : timesPart);
        sendNotice(userId, TPL_ENTRY_SUCCESS, p, bizId);
    }

    /** 会员卡激活成功通知 */
    public void notifyActivateSuccess(Long userId, String cardName, String endTime, String bizId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cardName", cardName);
        p.put("endTime", endTime == null ? "-" : endTime);
        sendNotice(userId, TPL_ACTIVATE_SUCCESS, p, bizId);
    }

    // ==================== 核心发送逻辑（异步） ====================

    /**
     * 异步发送一条关键通知短信。调用方无返回值、无异常，主链路零侵入。
     */
    public void sendNotice(Long userId, String templateType, Map<String, String> params, String bizId) {
        if (!enabled || userId == null) {
            return;
        }
        try {
            executor.submit(() -> doSend(userId, templateType, params, bizId));
        } catch (Exception e) {
            // 线程池拒绝等极端情况：仅记日志，不影响业务
            log.warn("短信任务提交失败 userId={} type={}: {}", userId, templateType, e.getMessage());
        }
    }

    private void doSend(Long userId, String templateType, Map<String, String> params, String bizId) {
        long start = System.currentTimeMillis();
        String content = render(templateType, params);
        String actualChannel = CHANNEL_LOG.equalsIgnoreCase(channel) ? CHANNEL_LOG : resolveCloudChannel();

        // 1. 查手机号
        String rawPhone = null;
        try {
            Optional<User> u = userMapper.findById(userId);
            if (u.isPresent()) {
                rawPhone = u.get().getPhone();
            }
        } catch (Exception e) {
            log.warn("短信发送前查询用户失败 userId={}: {}", userId, e.getMessage());
        }
        if (rawPhone == null || rawPhone.isBlank()) {
            save(userId, null, templateType, actualChannel, content, ST_SKIPPED, "用户未绑定手机号", bizId, start);
            return;
        }

        // 2. 限频（同用户同模板窗口内只发一次）
        String rlKey = "sms:rl:" + userId + ":" + templateType;
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(rlKey, "1", Duration.ofSeconds(rateLimitSeconds));
            if (!Boolean.TRUE.equals(first)) {
                save(userId, rawPhone, templateType, actualChannel, content, ST_SKIPPED,
                        "限频拦截（" + rateLimitSeconds + "s 内重复触发）", bizId, start);
                return;
            }
        } catch (Exception e) {
            // Redis 异常不阻塞短信发送（限频降级为放行）
            log.warn("短信限频检查失败，降级放行 userId={}: {}", userId, e.getMessage());
        }

        // 3. 按通道发送
        if (CHANNEL_ALIYUN.equals(actualChannel)) {
            try {
                sendViaAliyun(rawPhone, templateType, params);
                save(userId, rawPhone, templateType, actualChannel, content, ST_SUCCESS, null, bizId, start);
            } catch (Exception e) {
                log.error("阿里云短信发送失败 userId={} type={}", userId, templateType, e);
                save(userId, rawPhone, templateType, actualChannel, content, ST_FAILED, e.getMessage(), bizId, start);
            }
        } else {
            // LOG 通道：开发/演示环境真实输出到日志，便于验证触发链路
            log.info("[SMS→{}] {} | type={} bizId={}", maskPhone(rawPhone), content, templateType, bizId);
            save(userId, rawPhone, templateType, actualChannel, content, ST_SUCCESS, null, bizId, start);
        }
    }

    /**
     * 阿里云短信发送（预留真实通道）。
     * <p>
     * 生产启用步骤：① pom 引入 dysmsapi20170525 SDK；② 配置 sms.aliyun.access-key-id/secret
     * 与已审核的模板 code；③ 在此用 Client 发送。当前未引 SDK，未配置凭证时由
     * {@link #resolveCloudChannel()} 自动降级为 LOG 通道，因此该方法不会被走到。
     */
    private void sendViaAliyun(String phone, String templateType, Map<String, String> params) {
        throw new UnsupportedOperationException(
                "阿里云短信通道尚未接入 SDK，请引入 dysmsapi20170525 并完成模板配置后实现");
    }

    /**
     * 判定云通道是否可用；凭证不全时降级 LOG（保证开发环境与未配置环境链路可演示、不报错）。
     */
    private String resolveCloudChannel() {
        if (CHANNEL_ALIYUN.equalsIgnoreCase(channel)
                && aliyunAccessKeyId != null && !aliyunAccessKeyId.isBlank()
                && aliyunAccessKeySecret != null && !aliyunAccessKeySecret.isBlank()) {
            return CHANNEL_ALIYUN;
        }
        if (CHANNEL_ALIYUN.equalsIgnoreCase(channel)) {
            log.warn("sms.channel=aliyun 但 AccessKey 未配置，自动降级为 LOG 通道");
        }
        return CHANNEL_LOG;
    }

    // ==================== 工具 ====================

    private String render(String templateType, Map<String, String> params) {
        String tpl = TEMPLATES.getOrDefault(templateType, "【山达健身】您有一条新的通知。");
        if (params == null) return tpl;
        for (Map.Entry<String, String> e : params.entrySet()) {
            tpl = tpl.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return tpl;
    }

    /** 手机号脱敏：保留前 3 后 4 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    /** 落库留痕，单条失败仅记日志（留痕失败不能反过来影响主流程） */
    private void save(Long userId, String rawPhone, String templateType, String channel,
                      String content, String status, String failReason, String bizId, long startMs) {
        try {
            SmsRecord r = new SmsRecord();
            r.setId(idGenerator.nextId());
            r.setUserId(userId);
            r.setPhone(rawPhone == null ? null : maskPhone(rawPhone));
            r.setTemplateType(templateType);
            r.setChannel(channel);
            r.setContent(truncate(content, 500));
            r.setStatus(status);
            r.setFailReason(truncate(failReason, 255));
            r.setBizId(bizId);
            r.setCostMs((int) Math.min(System.currentTimeMillis() - startMs, Integer.MAX_VALUE));
            smsRecordMapper.insert(r);
        } catch (Exception e) {
            log.error("短信留痕写入失败 userId={} type={} status={}", userId, templateType, status, e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
