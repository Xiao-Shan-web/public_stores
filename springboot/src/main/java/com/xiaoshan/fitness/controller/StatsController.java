package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.mapper.EntryRecordMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.mapper.StatMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计接口（管理端）
 * GET /api/v1/admin/stats/**
 * 全部为只读聚合查询；days 参数统一限制在 1~365，趋势序列按天补零。
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Slf4j
public class StatsController {

    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FULL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final MembershipMapper membershipMapper;
    private final EntryRecordMapper entryRecordMapper;
    private final StatMapper statMapper;

    /**
     * 概览汇总：会员 / 会员卡 / 核销 / 营收核心指标
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        // 会员
        data.put("totalMembers", userMapper.countAll(""));
        data.put("todayNewMembers", userMapper.countTodayNew());
        // 会员卡
        data.put("activeCards", membershipMapper.countActive());
        data.put("unactivatedCards", statMapper.countUnactivated());
        data.put("expiringSoonCards", statMapper.countExpiringSoon());
        // 核销
        data.put("todayEntries", entryRecordMapper.countToday());
        // 营收（card_orders.status = PAID）
        data.put("todayIncome", statMapper.todayIncome());
        data.put("monthIncome", statMapper.monthIncome());
        data.put("totalIncome", statMapper.totalIncome());
        data.put("paidOrders", statMapper.paidOrderCount());
        data.put("pendingOrders", countByStatus("PENDING"));
        return Result.ok(data);
    }

    /**
     * 会员注册趋势（近 N 天，补零）
     */
    @GetMapping("/members/trend")
    public Result<Map<String, Object>> memberTrend(@RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> list = fillDailySeries(clampDays(days), statMapper.memberTrend(clampDays(days)), "c");
        return Result.ok(withList(list));
    }

    /**
     * 会员卡状态分布
     */
    @GetMapping("/memberships/status")
    public Result<Map<String, Object>> membershipStatus() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : statMapper.membershipStatusCounts()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", r.get("status"));
            item.put("count", asLong(r.get("c")));
            list.add(item);
        }
        return Result.ok(withList(list));
    }

    /**
     * 核销趋势（近 N 天，补零）
     */
    @GetMapping("/entries/trend")
    public Result<Map<String, Object>> entryTrend(@RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> list = fillDailySeries(clampDays(days), statMapper.entryTrend(clampDays(days)), "c");
        return Result.ok(withList(list));
    }

    /**
     * 核销时段分布（0-23 时，补零）
     */
    @GetMapping("/entries/by-hour")
    public Result<Map<String, Object>> entryByHour(@RequestParam(defaultValue = "30") int days) {
        int d = clampDays(days);
        Map<Integer, Long> byHour = new HashMap<>();
        for (Map<String, Object> r : statMapper.entryByHour(d)) {
            byHour.put(((Number) r.get("h")).intValue(), asLong(r.get("c")));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", h);
            item.put("count", byHour.getOrDefault(h, 0L));
            list.add(item);
        }
        return Result.ok(withList(list));
    }

    /**
     * 门店核销分布（按持卡购卡门店快照；全店通用卡单独归组）
     */
    @GetMapping("/entries/by-store")
    public Result<Map<String, Object>> entryByStore(@RequestParam(defaultValue = "30") int days) {
        int d = clampDays(days);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : statMapper.entryByStore(d)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", r.get("name"));
            item.put("count", asLong(r.get("c")));
            list.add(item);
        }
        return Result.ok(withList(list));
    }

    /**
     * 营收趋势（近 N 天，订单数 + 收入，补零）
     */
    @GetMapping("/income/trend")
    public Result<Map<String, Object>> incomeTrend(@RequestParam(defaultValue = "30") int days) {
        int d = clampDays(days);
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> r : statMapper.incomeTrend(d)) {
            byDate.put(String.valueOf(r.get("d")), r);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = d - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String full = day.format(FULL_FMT);
            Map<String, Object> raw = byDate.get(full);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.format(SHORT_FMT));
            item.put("orders", raw == null ? 0L : asLong(raw.get("orders")));
            item.put("income", raw == null ? BigDecimal.ZERO : raw.get("income"));
            list.add(item);
        }
        return Result.ok(withList(list));
    }

    /**
     * 营收汇总：累计/今日/本月收入 + 已付订单数 + 订单状态分布
     */
    @GetMapping("/income/summary")
    public Result<Map<String, Object>> incomeSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalIncome", statMapper.totalIncome());
        data.put("todayIncome", statMapper.todayIncome());
        data.put("monthIncome", statMapper.monthIncome());
        data.put("paidOrders", statMapper.paidOrderCount());
        List<Map<String, Object>> statusList = new ArrayList<>();
        for (Map<String, Object> r : statMapper.orderStatusCounts()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", r.get("status"));
            item.put("count", asLong(r.get("c")));
            statusList.add(item);
        }
        data.put("statusList", statusList);
        return Result.ok(data);
    }

    /**
     * 卡类型销量与收入排行（近 N 天已支付订单）
     */
    @GetMapping("/cards/sales")
    public Result<Map<String, Object>> cardSales(@RequestParam(defaultValue = "30") int days) {
        int d = clampDays(days);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : statMapper.cardSales(d)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", r.get("name"));
            item.put("sales", asLong(r.get("sales")));
            item.put("income", r.get("income"));
            list.add(item);
        }
        return Result.ok(withList(list));
    }

    /**
     * 会员活跃概览：当日 / 近 7 天 / 近 30 天去重活跃会员数。
     * 活跃口径 = 窗口期内至少 1 次成功到店核销。
     */
    @GetMapping("/members/active")
    public Result<Map<String, Object>> memberActive() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayActive", statMapper.activeUsers(1));
        data.put("weekActive", statMapper.activeUsers(7));
        data.put("monthActive", statMapper.activeUsers(30));
        return Result.ok(data);
    }

    /**
     * 新会员 7 日留存（cohort）。
     * 统计窗口内（近 days 天注册、且注册至今已满 7 天观察期）的新会员，
     * 计算其注册后 7 天内有成功核销的留存率，并按注册日给出序列。
     */
    @GetMapping("/members/retention")
    public Result<Map<String, Object>> memberRetention(@RequestParam(defaultValue = "30") int days) {
        // 至少要留出 7 天观察期，因此 cohort 跨度为 days-7；days 下限 8
        int d = Math.max(8, clampDays(days));
        int span = d - 7;

        Map<String, Long> newMap = new HashMap<>();
        for (Map<String, Object> r : statMapper.retentionCohortNew(d)) {
            newMap.put(String.valueOf(r.get("d")), asLong(r.get("c")));
        }
        Map<String, Long> retainedMap = new HashMap<>();
        for (Map<String, Object> r : statMapper.retentionCohortRetained(d)) {
            retainedMap.put(String.valueOf(r.get("d")), asLong(r.get("c")));
        }

        // cohort 区间：[span 天前, 7 天前]，按天补零
        List<Map<String, Object>> list = new ArrayList<>();
        long totalNew = 0;
        long totalRetained = 0;
        LocalDate today = LocalDate.now();
        for (int i = span; i >= 1; i--) {
            LocalDate regDay = today.minusDays(i);
            String full = regDay.format(FULL_FMT);
            long n = newMap.getOrDefault(full, 0L);
            long r = retainedMap.getOrDefault(full, 0L);
            totalNew += n;
            totalRetained += r;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", regDay.format(SHORT_FMT));
            item.put("newUsers", n);
            item.put("retainedUsers", r);
            item.put("rate", n == 0 ? 0L : Math.round(r * 10000.0 / n) / 100.0);
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newCount", totalNew);
        data.put("retainedCount", totalRetained);
        data.put("rate", totalNew == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.round(totalRetained * 10000.0 / totalNew) / 100.0));
        data.put("list", list);
        return Result.ok(data);
    }

    /**
     * 会员卡续费 / 复购统计：
     * 近 N 天续费单数与金额、续费单占比（订单口径）、
     * 累计购卡会员数与复购会员数、复购率（会员口径）。
     */
    @GetMapping("/cards/renewal")
    public Result<Map<String, Object>> cardRenewal(@RequestParam(defaultValue = "30") int days) {
        int d = clampDays(days);
        Map<String, Object> renewal = statMapper.renewalStats(d);
        long renewalOrders = asLong(renewal == null ? null : renewal.get("orders"));
        long paidOrders = statMapper.paidOrderCountInDays(d);
        long totalBuyers = statMapper.totalBuyers();
        long repeatBuyers = statMapper.repeatBuyers();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("renewalOrders", renewalOrders);
        data.put("renewalIncome", renewal == null ? BigDecimal.ZERO : renewal.get("income"));
        data.put("paidOrders", paidOrders);
        data.put("renewalRate", paidOrders == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.round(renewalOrders * 10000.0 / paidOrders) / 100.0));
        data.put("totalBuyers", totalBuyers);
        data.put("repeatBuyers", repeatBuyers);
        data.put("repeatRate", totalBuyers == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.round(repeatBuyers * 10000.0 / totalBuyers) / 100.0));
        return Result.ok(data);
    }

    // ==================== 私有工具 ====================

    /**
     * 趋势序列补零：把 DB 分组结果对齐到最近 N 天（含今天），缺失日期填 0
     */
    private List<Map<String, Object>> fillDailySeries(int days, List<Map<String, Object>> raw, String countKey) {
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> r : raw) {
            byDate.put(String.valueOf(r.get("d")), asLong(r.get(countKey)));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String full = day.format(FULL_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.format(SHORT_FMT));
            item.put("count", byDate.getOrDefault(full, 0L));
            list.add(item);
        }
        return list;
    }

    /**
     * 从订单状态分布中取指定状态的数量
     */
    private long countByStatus(String status) {
        for (Map<String, Object> r : statMapper.orderStatusCounts()) {
            if (status.equals(String.valueOf(r.get("status")))) {
                return asLong(r.get("c"));
            }
        }
        return 0;
    }

    private int clampDays(int days) {
        if (days < 1) return 1;
        return Math.min(days, 365);
    }

    private long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }

    private Map<String, Object> withList(List<Map<String, Object>> list) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return data;
    }

}
