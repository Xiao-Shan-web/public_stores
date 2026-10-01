package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Coupon;
import com.xiaoshan.fitness.mapper.CouponMapper;
import com.xiaoshan.fitness.util.CouponCalculator;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端优惠券接口
 * GET    /api/v1/admin/coupons              券列表（含已领/已用统计）
 * POST   /api/v1/admin/coupons              新建券
 * PUT    /api/v1/admin/coupons/{id}         编辑券
 * PUT    /api/v1/admin/coupons/{id}/status  上下架
 * DELETE /api/v1/admin/coupons/{id}         删除（软删）
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@Slf4j
public class AdminCouponController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JwtUtil jwtUtil;
    private final CouponMapper couponMapper;
    private final SnowflakeIdGenerator idGenerator;

    /** 券列表 + 发放/核销统计 */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", couponMapper.couponStats());
        data.put("templates", couponMapper.findAll());
        return Result.ok(data);
    }

    /** 新建券 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CouponDTO dto, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String err = validate(dto);
        if (err != null) {
            return Result.fail(400, err);
        }

        Coupon c = toEntity(dto);
        c.setId(idGenerator.nextId());
        couponMapper.insert(c);
        log.info("管理员{}创建优惠券[{}]{}", adminId, c.getId(), c.getName());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", c.getId());
        return Result.ok(data, "创建成功");
    }

    /** 编辑券 */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody CouponDTO dto,
                                 HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Coupon exist = couponMapper.findById(id);
        if (exist == null || (exist.getIsDeleted() != null && exist.getIsDeleted() == 1)) {
            return Result.fail(404, "优惠券不存在");
        }
        String err = validate(dto);
        if (err != null) {
            return Result.fail(400, err);
        }
        // 发行总量不得小于已领取数量
        int issued = exist.getIssuedCount() == null ? 0 : exist.getIssuedCount();
        if (dto.getTotalCount() != null && dto.getTotalCount() > 0 && dto.getTotalCount() < issued) {
            return Result.fail(400, "发行总量不能小于已领取数量（" + issued + "）");
        }

        Coupon c = toEntity(dto);
        c.setId(id);
        couponMapper.update(c);
        log.info("管理员{}编辑优惠券{}", adminId, id);
        return Result.ok("修改成功");
    }

    /** 上下架 */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                       HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Integer status = body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status")));
        if (status == null || (status != 0 && status != 1)) {
            return Result.fail(400, "状态参数不正确");
        }
        int rows = couponMapper.updateStatus(id, status);
        if (rows == 0) {
            return Result.fail(404, "优惠券不存在");
        }
        log.info("管理员{}将优惠券{}置为{}", adminId, id, status == 1 ? "上架" : "下架");
        return Result.ok(status == 1 ? "已上架" : "已下架");
    }

    /** 删除（软删） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        int rows = couponMapper.softDelete(id);
        if (rows == 0) {
            return Result.fail(404, "优惠券不存在");
        }
        log.warn("管理员{}删除优惠券{}", adminId, id);
        return Result.ok("已删除");
    }

    // ==================== 私有工具 ====================

    private String validate(CouponDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return "券名称不能为空";
        }
        if (dto.getThreshold() != null && dto.getThreshold().compareTo(BigDecimal.ZERO) < 0) {
            return "使用门槛不能为负数";
        }
        String type = dto.getType() == null ? "FULL_REDUCE" : dto.getType();
        if (!CouponCalculator.isValidType(type)) {
            return "券类型不正确";
        }
        if (CouponCalculator.TYPE_DISCOUNT.equals(type)) {
            // 折扣券：discount 为折扣率（0<discount<1），amount 可填 0
            if (dto.getDiscount() == null
                    || dto.getDiscount().compareTo(BigDecimal.ZERO) <= 0
                    || dto.getDiscount().compareTo(BigDecimal.ONE) >= 0) {
                return "折扣率必须在 0 与 1 之间（如 0.85 表示 85 折）";
            }
            if (dto.getMaxDiscount() != null && dto.getMaxDiscount().compareTo(BigDecimal.ZERO) < 0) {
                return "最高优惠上限不能为负数";
            }
        } else if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "抵扣面额必须大于 0";
        }
        String validType = dto.getValidType() == null ? "DAYS_AFTER_RECEIVE" : dto.getValidType();
        if (!"FIXED".equals(validType) && !"DAYS_AFTER_RECEIVE".equals(validType)) {
            return "有效期类型不正确";
        }
        if ("DAYS_AFTER_RECEIVE".equals(validType) && (dto.getValidDays() == null || dto.getValidDays() <= 0)) {
            return "领取后有效天数必须大于 0";
        }
        if ("FIXED".equals(validType)) {
            if (dto.getStartTime() == null || dto.getEndTime() == null) {
                return "固定有效期需填写开始与结束时间";
            }
            if (dto.getEndTime().isBefore(dto.getStartTime())) {
                return "结束时间不能早于开始时间";
            }
        }
        if (dto.getPerUserLimit() != null && dto.getPerUserLimit() <= 0) {
            return "每人限领张数必须大于 0";
        }
        return null;
    }

    private Coupon toEntity(CouponDTO dto) {
        Coupon c = new Coupon();
        c.setName(dto.getName().trim());
        c.setType(dto.getType() == null ? "FULL_REDUCE" : dto.getType());
        c.setThreshold(dto.getThreshold() == null ? BigDecimal.ZERO : dto.getThreshold());
        c.setAmount(dto.getAmount() == null ? BigDecimal.ZERO : dto.getAmount());
        c.setDiscount(dto.getDiscount());
        c.setMaxDiscount(dto.getMaxDiscount());
        c.setTotalCount(dto.getTotalCount() == null ? 0 : dto.getTotalCount());
        c.setPerUserLimit(dto.getPerUserLimit() == null ? 1 : dto.getPerUserLimit());
        c.setValidType(dto.getValidType() == null ? "DAYS_AFTER_RECEIVE" : dto.getValidType());
        c.setValidDays(dto.getValidDays());
        c.setStartTime(dto.getStartTime());
        c.setEndTime(dto.getEndTime());
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        return c;
    }

    private Long currentAdminId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_ADMIN.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    /** 券新增/编辑请求体；时间字段用 ISO 字符串（yyyy-MM-ddTHH:mm） */
    @Data
    public static class CouponDTO {
        private String name;
        /** FULL_REDUCE-满减 / DIRECT-直减 / DISCOUNT-折扣 */
        private String type;
        private BigDecimal threshold;
        /** 抵扣面额（FULL_REDUCE/DIRECT）；折扣券可不填 */
        private BigDecimal amount;
        /** 折扣率（DISCOUNT，0.85 = 85 折） */
        private BigDecimal discount;
        /** 最高优惠上限（DISCOUNT，可空表示不封顶） */
        private BigDecimal maxDiscount;
        private Integer totalCount;
        private Integer perUserLimit;
        private String validType;
        private Integer validDays;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer status;
    }

}
