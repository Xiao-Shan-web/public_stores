package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Coupon;
import com.xiaoshan.fitness.mapper.CouponMapper;
import com.xiaoshan.fitness.service.CouponService;
import com.xiaoshan.fitness.util.CouponCalculator;
import com.xiaoshan.fitness.util.DistributedLock;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端优惠券接口
 * GET  /api/v1/user/coupons/center   领券中心（可领取的券列表）
 * POST /api/v1/user/coupons/{id}/receive  领取
 * GET  /api/v1/user/coupons          我的券（scope=UNUSED/USED/EXPIRED/ALL）
 * GET  /api/v1/user/coupons/usable   结算试算（全部未使用券 + 可用标记 + 本单抵扣 + 最优券）
 */
@RestController
@RequestMapping("/api/v1/user/coupons")
@RequiredArgsConstructor
@Slf4j
public class UserCouponController {

    private final JwtUtil jwtUtil;
    private final CouponMapper couponMapper;
    private final CouponService couponService;
    private final DistributedLock distributedLock;

    /**
     * 领券中心：上架且未领完的券 + 当前用户已领数量（用于前端置灰）
     */
    @GetMapping("/center")
    public Result<Map<String, Object>> center(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        List<Coupon> coupons = couponMapper.findAvailable();
        List<Map<String, Object>> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            item.put("type", c.getType());
            item.put("threshold", c.getThreshold());
            item.put("amount", c.getAmount());
            item.put("discount", c.getDiscount());
            item.put("maxDiscount", c.getMaxDiscount());
            // 面额展示文案（折扣券为「8.5折」）
            item.put("faceText", CouponCalculator.faceText(c.getType(), c.getAmount(), c.getDiscount(), c.getMaxDiscount()));
            item.put("totalCount", c.getTotalCount());
            item.put("issuedCount", c.getIssuedCount());
            item.put("perUserLimit", c.getPerUserLimit());
            item.put("validType", c.getValidType());
            item.put("validDays", c.getValidDays());
            item.put("endTime", c.getEndTime());
            item.put("startTime", c.getStartTime());

            long owned = couponMapper.countUserCoupon(c.getId(), userId);
            int limit = c.getPerUserLimit() == null ? 1 : c.getPerUserLimit();
            item.put("ownedCount", owned);
            item.put("receivable", owned < limit);
            // 剩余数量（0 表示不限量）
            int total = c.getTotalCount() == null ? 0 : c.getTotalCount();
            int issued = c.getIssuedCount() == null ? 0 : c.getIssuedCount();
            item.put("remainCount", total == 0 ? -1 : Math.max(total - issued, 0));
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("usableCount", couponMapper.countUsable(userId));
        return Result.ok(data);
    }

    /**
     * 领取优惠券（分布式锁 + DB 双重防超发，见 CouponService）
     */
    @PostMapping("/{id}/receive")
    public Result<Map<String, Object>> receive(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        // 分布式锁：同一用户同一券串行处理，锁内再做 Redis 短锁与 DB 校验
        Map<String, Object> data = distributedLock.executeWithLock(
                "coupon:receive:lock:" + userId + ":" + id,
                Duration.ofSeconds(5),
                () -> couponService.receive(userId, id)
        );
        return Result.ok(data, "领取成功");
    }

    /**
     * 释放被订单占用的券：POST /api/v1/user/coupons/{id}/release
     * <p>
     * 用于"下单未支付 → 券被 LOCKED 占用"的场景：自动取消占用的待支付订单并把券退回，
     * 未过期归还 UNUSED，已过期置 EXPIRED，保证用户不会因未支付订单永久失去优惠券。
     */
    @PostMapping("/{id}/release")
    public Result<Map<String, Object>> release(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String status = couponService.releaseByCoupon(userId, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("usableCount", couponMapper.countUsable(userId));
        return Result.ok(data, "已取消占用");
    }

    /**
     * 我的优惠券：scope=UNUSED（默认，可用）/ LOCKED（已占用）/ USED（已使用）/ EXPIRED（已过期）/ ALL（全部）
     */
    @GetMapping
    public Result<Map<String, Object>> myCoupons(HttpServletRequest request,
                                                 @RequestParam(defaultValue = "UNUSED") String scope) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        List<Map<String, Object>> raw = couponService.listUserCoupons(userId, scope);
        List<Map<String, Object>> list = new ArrayList<>(raw.size());
        for (Map<String, Object> r : raw) {
            Map<String, Object> item = new LinkedHashMap<>(r);
            // 过期标记：UNUSED 但已过 end_time
            item.put("expired", "1".equals(String.valueOf(r.get("expired"))));
            // 面额展示文案（折扣券为「8.5折」）
            item.put("faceText", CouponCalculator.faceText(
                    r.get("couponType") == null ? null : String.valueOf(r.get("couponType")),
                    toDecimal(r.get("amount")),
                    r.get("discount") == null ? null : toDecimal(r.get("discount")),
                    r.get("maxDiscount") == null ? null : toDecimal(r.get("maxDiscount"))));
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("usableCount", couponMapper.countUsable(userId));
        return Result.ok(data);
    }

    /**
     * 结算试算：传订单金额，返回该用户<b>全部未使用未过期券</b>的试算结果。
     * <p>
     * 每张券带 {@code usable}（是否满足门槛）、{@code cutAmount}（本单可抵扣）、
     * {@code reason}（不可用原因，前端置灰提示）、{@code faceText}（面额文案）；
     * 抵扣金额与下单调用的 CouponCalculator 完全同口径，前端可放心直接展示。
     * 同时返回最优券 {@code bestUserCouponId} / {@code bestDiscount}（默认选中用）。
     */
    @GetMapping("/usable")
    public Result<Map<String, Object>> usable(HttpServletRequest request,
                                              @RequestParam BigDecimal amount) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        List<Map<String, Object>> raw = couponService.listUserCoupons(userId, "UNUSED");
        List<Map<String, Object>> list = new ArrayList<>(raw.size());
        BigDecimal best = BigDecimal.ZERO;
        Long bestId = null;
        for (Map<String, Object> r : raw) {
            Map<String, Object> item = new LinkedHashMap<>(r);
            String type = r.get("couponType") == null ? null : String.valueOf(r.get("couponType"));
            BigDecimal threshold = toDecimal(r.get("threshold"));
            BigDecimal face = toDecimal(r.get("amount"));
            BigDecimal discount = r.get("discount") == null ? null : toDecimal(r.get("discount"));
            BigDecimal maxDiscount = r.get("maxDiscount") == null ? null : toDecimal(r.get("maxDiscount"));

            BigDecimal cut = CouponCalculator.cutAmount(type, face, discount, maxDiscount, threshold, amount);
            boolean usableNow = cut.compareTo(BigDecimal.ZERO) > 0;
            item.put("usable", usableNow);
            item.put("cutAmount", cut);
            item.put("reason", usableNow ? null : CouponCalculator.unavailableReason(threshold, amount));
            item.put("faceText", CouponCalculator.faceText(type, face, discount, maxDiscount));
            list.add(item);

            if (usableNow && cut.compareTo(best) > 0) {
                best = cut;
                bestId = ((Number) r.get("id")).longValue();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("usableCount", list.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("usable"))).count());
        data.put("bestUserCouponId", bestId);
        data.put("bestDiscount", best);
        return Result.ok(data);
    }

    // ==================== 私有工具 ====================

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

}
