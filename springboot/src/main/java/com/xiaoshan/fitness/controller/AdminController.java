package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.annotation.Idempotent;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.EntryRecordMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.mapper.StatMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.service.MembershipActivationService;
import com.xiaoshan.fitness.util.DistributedLock;
import com.xiaoshan.fitness.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端业务接口
 * 会员/核销记录/系统消息/控制台统计
 * 卡类型管理已迁移至 CardTypeController
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserMapper userMapper;
    private final MembershipMapper membershipMapper;
    private final EntryRecordMapper entryRecordMapper;
    private final MessageMapper messageMapper;
    private final StatMapper statMapper;
    private final MembershipActivationService membershipActivationService;
    private final DistributedLock distributedLock;

    /**
     * 会员管理：分页查询用户列表（关联最新会员卡）
     */
    @GetMapping("/members")
    public Result<Map<String, Object>> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String kw = keyword == null ? "" : keyword;
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = userMapper.findAll(kw, offset, size);
        long total = userMapper.countAll(kw);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 核销记录：查询全部核销记录（最新 limit 条）
     */
    @GetMapping("/records")
    public Result<Map<String, Object>> getRecords(
            @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> list = entryRecordMapper.findAll(limit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return Result.ok(data);
    }

    /**
     * 系统消息：查询系统公告（type=SYSTEM）
     */
    @GetMapping("/messages")
    public Result<Map<String, Object>> getMessages(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit) {
        List<Message> list = messageMapper.findSystemMessages(limit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return Result.ok(data);
    }

    /**
     * 控制台统计：总会员数 / 今日新增会员 / 有效会员卡 / 今日核销 / 本月收入
     */
    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMembers", userMapper.countAll(""));
        data.put("todayNewMembers", userMapper.countTodayNew());
        data.put("activeCards", membershipMapper.countActive());
        data.put("todayEntries", entryRecordMapper.countToday());
        // 本月收入：card_orders.status=PAID 按支付时间（COALESCE(paid_at, pay_time, created_at)）归入自然月
        data.put("monthIncome", statMapper.monthIncome());
        return Result.ok(data);
    }

    /**
     * 控制台：会员卡类型占比
     */
    @GetMapping("/dashboard/card-distribution")
    public Result<Map<String, Object>> getCardDistribution() {
        List<Map<String, Object>> list = membershipMapper.distributionByType();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return Result.ok(data);
    }

    /**
     * 控制台：近 7 天核销趋势（补全缺失日期为 0）
     */
    @GetMapping("/dashboard/trend")
    public Result<Map<String, Object>> getTrend() {
        List<Map<String, Object>> raw = entryRecordMapper.trend7Days();

        // 将查询结果按 yyyy-MM-dd 归集
        SimpleDateFormat fullFmt = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Integer> dateCountMap = new LinkedHashMap<>();
        for (Map<String, Object> r : raw) {
            String date = fullFmt.format(r.get("date"));
            int count = ((Number) r.get("count")).intValue();
            dateCountMap.put(date, count);
        }

        // 补全近 7 天（含今天）
        SimpleDateFormat shortFmt = new SimpleDateFormat("MM-dd");
        List<Map<String, Object>> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, -i);
            String fullDate = fullFmt.format(cal.getTime());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", shortFmt.format(cal.getTime()));
            item.put("count", dateCountMap.getOrDefault(fullDate, 0));
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return Result.ok(data);
    }

    /**
     * 会员卡管理：分页查询全部会员卡记录（关联用户手机号 + 卡类型名称）
     * 支持筛选：status 状态、storeId 门店、cardTypeId 卡种、phone 手机号模糊、cardNo 卡号尾号模糊
     * phone 与 cardNo 为 OR 关系，前端单搜索框同时匹配
     */
    @GetMapping("/memberships")
    public Result<Map<String, Object>> getMemberships(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long cardTypeId,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String cardNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = membershipMapper.findAll(status, storeId, cardTypeId, phone, cardNo, offset, size);
        long total = membershipMapper.countAll(status, storeId, cardTypeId, phone, cardNo);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 会员卡管理：会员卡详情（完整信息 + 操作记录）
     * GET /api/v1/admin/memberships/{id}
     */
    @GetMapping("/memberships/{id}")
    public Result<Map<String, Object>> getMembershipDetail(@PathVariable Long id) {
        Map<String, Object> detail = membershipMapper.findDetailById(id);
        if (detail == null) {
            return Result.fail("会员卡不存在");
        }
        // 操作记录（审计日志按该卡 URI 前缀匹配）
        List<Map<String, Object>> logs = membershipMapper.findLogsByMembershipId(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("detail", detail);
        data.put("logs", logs);
        return Result.ok(data);
    }

    /**
     * 会员卡管理：手动停用异常会员卡（status → DISABLED）
     * reason 作为 query 参数传递，由审计拦截器自动记录到操作日志
     */
    @PutMapping("/memberships/{id}/disable")
    public Result<Map<String, Object>> disableMembership(@PathVariable Long id,
                                                          @RequestParam(required = false) String reason) {
        int rows = membershipMapper.disable(id);
        if (rows == 0) {
            return Result.fail("会员卡不存在或已停用");
        }
        log.warn("管理员手动停用会员卡：ID={}，原因={}", id, reason);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", "DISABLED");
        return Result.ok(data, "已停用");
    }

    /**
     * 会员卡管理：手动延期（延长到期时间，EXPIRED 卡延期后恢复为 ACTIVE）
     * days 延期天数、reason 原因均作为 query 参数，由审计拦截器记录
     * PUT /api/v1/admin/memberships/{id}/extend?days=30&reason=xxx
     */
    @PutMapping("/memberships/{id}/extend")
    public Result<Map<String, Object>> extendMembership(@PathVariable Long id,
                                                        @RequestParam int days,
                                                        @RequestParam(required = false) String reason) {
        if (days <= 0 || days > 3650) {
            return Result.fail("延期天数须在 1~3650 之间");
        }
        Map<String, Object> detail = membershipMapper.findDetailById(id);
        if (detail == null) {
            return Result.fail("会员卡不存在");
        }
        String status = (String) detail.get("status");
        if (!"ACTIVE".equals(status) && !"EXPIRED".equals(status)) {
            return Result.fail("当前状态不支持延期（仅生效中/已过期可延期）");
        }
        // 生效中：在原到期时间基础上 +days；已过期：从当前时间起 +day（恢复生效）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Object endObj = detail.get("endTime");
        java.time.LocalDateTime base = endObj instanceof java.time.LocalDateTime
                ? (java.time.LocalDateTime) endObj : null;
        if (base == null || base.isBefore(now)) {
            base = now;
        }
        java.time.LocalDateTime newEnd = base.plusDays(days);
        int rows = membershipMapper.extendEndTime(id, newEnd);
        if (rows == 0) {
            return Result.fail("延期失败，请刷新后重试");
        }
        log.warn("管理员手动延期会员卡：ID={}，+{}天，新到期={}，原因={}", id, days, newEnd, reason);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("endTime", newEnd);
        data.put("status", "EXPIRED".equals(status) ? "ACTIVE" : status);
        return Result.ok(data, "已延期 " + days + " 天");
    }

    /**
     * 会员卡管理：手动调整剩余次数（仅次卡，设置为指定新值）
     * value 新剩余次数、reason 原因均作为 query 参数，由审计拦截器记录
     * PUT /api/v1/admin/memberships/{id}/times?value=10&reason=xxx
     */
    @PutMapping("/memberships/{id}/times")
    public Result<Map<String, Object>> adjustTimes(@PathVariable Long id,
                                                   @RequestParam int value,
                                                   @RequestParam(required = false) String reason) {
        if (value < 0) {
            return Result.fail("剩余次数不能为负");
        }
        Map<String, Object> detail = membershipMapper.findDetailById(id);
        if (detail == null) {
            return Result.fail("会员卡不存在");
        }
        Integer totalTimes = detail.get("totalTimes") == null ? null
                : ((Number) detail.get("totalTimes")).intValue();
        if (totalTimes == null) {
            return Result.fail("该卡非次卡，不支持调整次数");
        }
        if (value > totalTimes) {
            return Result.fail("剩余次数不能超过总次数 " + totalTimes);
        }
        int rows = membershipMapper.setRemainingTimes(id, value);
        if (rows == 0) {
            return Result.fail("调整失败，请刷新后重试");
        }
        log.warn("管理员手动调整会员卡剩余次数：ID={}，新值={}，原因={}", id, value, reason);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("remainingTimes", value);
        return Result.ok(data, "已调整为 " + value + " 次");
    }

    /**
     * 管理员手动激活会员卡（UNACTIVATED → ACTIVE）
     * PUT /api/v1/admin/memberships/{id}/activate
     * 管理员激活无需刷脸，userId 传 null 跳过归属校验
     * <p>
     * 幂等窗口 5 秒：防止连点导致重复激活。业务幂等（已激活状态）由 DB 状态校验兜底。
     * <p>
     * 并发互斥：以 membershipId 为维度加 Redis 分布式锁（TTL 30 秒），
     * 防止多管理员/多请求并发激活同一会员卡导致状态错乱。
     * 锁失败抛 BusinessException(429) 由全局异常处理返回统一响应。
     */
    @PutMapping("/memberships/{id}/activate")
    @Idempotent(expireSeconds = 5)
    public Result<Map<String, Object>> activateMembership(@PathVariable Long id) {
        return distributedLock.executeWithLock(
                "membership:activate:" + id,
                java.time.Duration.ofSeconds(30),
                () -> {
                    Map<String, Object> data = membershipActivationService.activate(id, null);
                    boolean success = Boolean.TRUE.equals(data.get("success"));
                    if (success) {
                        log.warn("管理员手动激活会员卡：ID={}", id);
                        return Result.ok(data, (String) data.get("message"));
                    }
                    return Result.fail((String) data.get("message"));
                }
        );
    }

}
