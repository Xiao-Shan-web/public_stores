package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.Admin;
import com.xiaoshan.fitness.entity.AdminAuditLog;
import com.xiaoshan.fitness.mapper.AdminMapper;
import com.xiaoshan.fitness.mapper.GovernanceMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 管理员操作审计日志服务。
 * <p>
 * 由 {@code AdminAuditInterceptor} 在管理端写操作完成后异步调用，主链路零阻塞；
 * 单线程有序落库，队列满时丢弃最旧任务并告警（优先保业务，不允许审计拖垮请求）。
 * <p>
 * username 通过 adminId 查询并做本地短缓存（10 分钟），避免每条日志查库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final GovernanceMapper governanceMapper;
    private final AdminMapper adminMapper;
    private final SnowflakeIdGenerator idGenerator;

    private static final int PAGE_SIZE_MAX = 100;

    /** adminId → username 短缓存（管理员数量少，直接全量内存 map + 过期标记） */
    private final Map<Long, long[]> nameCacheTime = new LinkedHashMap<>();
    private final Map<Long, String> nameCache = new LinkedHashMap<>();
    private static final long NAME_CACHE_MS = 10 * 60 * 1000L;

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2000),
            r -> {
                Thread t = new Thread(r, "audit-logger");
                t.setDaemon(true);
                return t;
            },
            (r, exec) -> {
                // 队列满：丢弃队首最旧任务再尝试，保证极端情况下不阻塞业务线程
                if (!exec.isShutdown() && exec.getQueue().poll() != null) {
                    log.warn("审计日志队列已满，丢弃最旧一条审计任务");
                    exec.execute(r);
                }
            });

    /** 异步记录一条审计日志 */
    public void recordAsync(Long adminId, String module, String method, String uri,
                            String paramSummary, String ip, boolean success, int costMs) {
        try {
            String username = adminId == null ? null : resolveUsername(adminId);
            executor.submit(() -> doInsertWithUsername(adminId, username, module, method, uri,
                    paramSummary, ip, success, costMs));
        } catch (Exception e) {
            log.warn("审计日志提交失败 uri={}: {}", e.getMessage());
        }
    }

    /** 登录事件显式记录（成功/失败），username 直接可取，避免解析请求体 */
    public void recordLogin(String username, boolean success, String ip, int costMs) {
        try {
            executor.submit(() -> doInsertWithUsername(null, username, "auth", "POST",
                    "/api/v1/admin/login", null, ip, success, costMs));
        } catch (Exception e) {
            log.warn("登录审计日志提交失败: {}", e.getMessage());
        }
    }

    private void doInsertWithUsername(Long adminId, String username, String module, String method, String uri,
                                      String paramSummary, String ip, boolean success, int costMs) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            l.setId(idGenerator.nextId());
            l.setAdminId(adminId);
            l.setUsername(username);
            l.setModule(module);
            l.setAction(method);
            l.setMethod(method);
            l.setUri(truncate(uri, 255));
            l.setParamSummary(truncate(paramSummary, 500));
            l.setIp(ip);
            l.setResult(success ? "SUCCESS" : "FAIL");
            l.setCostMs(costMs);
            governanceMapper.insertAuditLog(l);
        } catch (Exception e) {
            log.error("审计日志落库失败 uri={}", uri, e);
        }
    }

    private String resolveUsername(Long adminId) {
        long now = System.currentTimeMillis();
        long[] ts = nameCacheTime.get(adminId);
        if (ts != null && now - ts[0] < NAME_CACHE_MS) {
            return nameCache.get(adminId);
        }
        try {
            Optional<Admin> a = adminMapper.findById(adminId);
            String name = a.map(Admin::getUsername).orElse(null);
            nameCache.put(adminId, name);
            nameCacheTime.put(adminId, new long[]{now});
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> adminList(String module, Long adminId, String action, int page, int size) {
        int p = Math.max(1, page);
        int s = size <= 0 ? 20 : Math.min(size, PAGE_SIZE_MAX);
        int offset = (p - 1) * s;
        List<AdminAuditLog> list = governanceMapper.adminListAuditLogs(
                blankToNull(module), adminId, blankToNull(action), offset, s);
        long total = governanceMapper.adminCountAuditLogs(
                blankToNull(module), adminId, blankToNull(action));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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
