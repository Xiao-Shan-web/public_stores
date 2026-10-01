package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.RiskEvent;
import com.xiaoshan.fitness.mapper.GovernanceMapper;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风控检测与事件管理。
 * <p>
 * 自动检测规则（全部基于 Redis 固定时间窗，性能开销极小，检测失败不影响主业务）：
 * <ul>
 *   <li>{@code LOGIN_FAIL_BURST}：同一管理员账号 5 分钟内登录失败 ≥5 次（疑似爆破），MEDIUM；</li>
 *   <li>{@code ENTRY_FREQ}：同一用户 1 分钟内到店核销 ≥4 次（正常不可能，疑似代刷/盗刷），HIGH；</li>
 *   <li>{@code ENTRY_NIGHT}：23:00–次日 05:00 夜间核销，LOW（每人每晚最多记一条）。</li>
 * </ul>
 * 事件落 risk_events，管理端可标记已处理/忽略。窗口内同一规则只产生一条事件（fired 标记去重）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private final StringRedisTemplate redisTemplate;
    private final GovernanceMapper governanceMapper;
    private final SnowflakeIdGenerator idGenerator;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 管理员登录失败：5 分钟窗口、阈值 5 次 */
    private static final int LOGIN_FAIL_WINDOW_SECONDS = 300;
    private static final int LOGIN_FAIL_THRESHOLD = 5;
    /** 用户核销：1 分钟窗口、阈值 4 次 */
    private static final int ENTRY_FREQ_WINDOW_SECONDS = 120; // key 保留 2 分钟，分钟桶天然过期
    private static final int ENTRY_FREQ_THRESHOLD = 4;

    private static final Set<String> HANDLE_STATUS = Set.of("HANDLED", "IGNORED");
    private static final int PAGE_SIZE_MAX = 50;

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 检测埋点（主链路调用，异常全部吞掉） ====================

    /**
     * 管理员登录失败埋点（含用户名为空/账号不存在的爆破尝试）。
     */
    public void recordAdminLoginFail(String username, String ip) {
        if (username == null || username.isBlank()) return;
        try {
            // 5 分钟桶（整除 5）
            long bucket = System.currentTimeMillis() / 1000 / LOGIN_FAIL_WINDOW_SECONDS;
            String cntKey = "risk:loginfail:" + username + ":" + bucket;
            Long n = redisTemplate.opsForValue().increment(cntKey);
            if (n != null && n == 1L) {
                redisTemplate.expire(cntKey, Duration.ofSeconds(LOGIN_FAIL_WINDOW_SECONDS + 30));
            }
            if (n != null && n >= LOGIN_FAIL_THRESHOLD) {
                String firedKey = "risk:loginfail:fired:" + username + ":" + bucket;
                if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(firedKey, "1", Duration.ofSeconds(LOGIN_FAIL_WINDOW_SECONDS + 30)))) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("windowSeconds", LOGIN_FAIL_WINDOW_SECONDS);
                    detail.put("failCount", n);
                    detail.put("ip", ip);
                    save("LOGIN_FAIL_BURST", "MEDIUM", "ADMIN", null, username, detail);
                    log.warn("风控-管理员登录失败爆发 username={} 5分钟内失败{}次 ip={}", username, n, ip);
                }
            }
        } catch (Exception e) {
            log.warn("登录失败风控检测异常（不影响登录流程）: {}", e.getMessage());
        }
    }

    /**
     * 用户到店核销成功埋点：检测高频核销与夜间核销。
     */
    public void recordUserEntry(Long userId, String phone, Long storeId) {
        if (userId == null) return;
        try {
            // 1. 高频核销：按分钟桶计数
            String minute = LocalDateTime.now().format(MINUTE_FMT);
            String cntKey = "risk:entrycnt:" + userId + ":" + minute;
            Long n = redisTemplate.opsForValue().increment(cntKey);
            if (n != null && n == 1L) {
                redisTemplate.expire(cntKey, Duration.ofSeconds(ENTRY_FREQ_WINDOW_SECONDS));
            }
            if (n != null && n >= ENTRY_FREQ_THRESHOLD) {
                String firedKey = "risk:entryfired:" + userId + ":" + minute;
                if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(firedKey, "1", Duration.ofSeconds(ENTRY_FREQ_WINDOW_SECONDS)))) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("window", "1分钟内");
                    detail.put("entryCount", n);
                    detail.put("storeId", storeId);
                    save("ENTRY_FREQ", "HIGH", "USER", userId, maskPhone(phone), detail);
                    log.warn("风控-高频核销 userId={} 1分钟内核销{}次 storeId={}", userId, n, storeId);
                }
            }

            // 2. 夜间核销：23:00–05:00，每人每晚最多一条
            int hour = LocalDateTime.now().getHour();
            boolean night = hour >= 23 || hour < 5;
            if (night) {
                String day = LocalDate.now().format(DAY_FMT);
                String nightKey = "risk:entrynight:" + userId + ":" + day;
                if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(nightKey, "1", Duration.ofHours(26)))) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("hour", hour);
                    detail.put("storeId", storeId);
                    detail.put("note", "非营业时段核销，请核实是否为正常营业门店");
                    save("ENTRY_NIGHT", "LOW", "USER", userId, maskPhone(phone), detail);
                    log.info("风控-夜间核销 userId={} hour={} storeId={}", userId, hour, storeId);
                }
            }
        } catch (Exception e) {
            log.warn("核销风控检测异常（不影响核销流程）: {}", e.getMessage());
        }
    }

    // ==================== 管理端查询/处置 ====================

    public Map<String, Object> adminList(String status, String level, String type, int page, int size) {
        int p = Math.max(1, page);
        int s = size <= 0 ? 10 : Math.min(size, PAGE_SIZE_MAX);
        int offset = (p - 1) * s;
        List<RiskEvent> list = governanceMapper.adminListRiskEvents(
                blankToNull(status), blankToNull(level), blankToNull(type), offset, s);
        long total = governanceMapper.adminCountRiskEvents(
                blankToNull(status), blankToNull(level), blankToNull(type));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("openCount", governanceMapper.countOpenRiskEvents());
        return data;
    }

    /** 处置事件：HANDLED 已处理 / IGNORED 已忽略 */
    public void handle(Long adminId, Long id, String status, String remark) {
        if (!HANDLE_STATUS.contains(status)) throw new BusinessException("处置状态不合法");
        if (remark != null && remark.length() > 255) throw new BusinessException("备注不能超过255字");
        int rows = governanceMapper.handleRiskEvent(id, status, adminId,
                (remark == null || remark.isBlank()) ? null : remark.trim());
        if (rows == 0) throw new BusinessException(404, "风控事件不存在");
    }

    // ==================== 工具 ====================

    private void save(String eventType, String level, String subjectType,
                      Long subjectId, String subjectName, Map<String, Object> detail) {
        try {
            RiskEvent e = new RiskEvent();
            e.setId(idGenerator.nextId());
            e.setEventType(eventType);
            e.setRiskLevel(level);
            e.setSubjectType(subjectType);
            e.setSubjectId(subjectId);
            e.setSubjectName(subjectName);
            e.setDetailJson(MAPPER.writeValueAsString(detail));
            e.setStatus("OPEN");
            governanceMapper.insertRiskEvent(e);
        } catch (Exception ex) {
            log.error("风控事件落库失败 type={} subject={}", eventType, subjectId, ex);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
