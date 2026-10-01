package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.Activity;
import com.xiaoshan.fitness.mapper.ActivityMapper;
import com.xiaoshan.fitness.mapper.CardTypeMapper;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端限时活动接口
 * GET    /api/v1/admin/activities              活动列表（含阶段标识）
 * POST   /api/v1/admin/activities              新建活动
 * PUT    /api/v1/admin/activities/{id}         编辑活动
 * PUT    /api/v1/admin/activities/{id}/status  上下架
 * DELETE /api/v1/admin/activities/{id}         删除（软删）
 */
@RestController
@RequestMapping("/api/v1/admin/activities")
@RequiredArgsConstructor
@Slf4j
public class AdminActivityController {

    private final JwtUtil jwtUtil;
    private final ActivityMapper activityMapper;
    private final CardTypeMapper cardTypeMapper;
    private final SnowflakeIdGenerator idGenerator;

    /** 活动列表 */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", activityMapper.findAllForAdmin());
        return Result.ok(data);
    }

    /** 新建活动 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody ActivityDTO dto, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        String err = validate(dto);
        if (err != null) {
            return Result.fail(400, err);
        }

        Activity a = toEntity(dto);
        a.setId(idGenerator.nextId());
        activityMapper.insert(a);
        log.info("管理员{}创建活动[{}]{}（卡类型={}，折扣={}）",
                adminId, a.getId(), a.getTitle(), a.getCardTypeId(), a.getDiscount());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", a.getId());
        return Result.ok(data, "创建成功");
    }

    /** 编辑活动 */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody ActivityDTO dto,
                                 HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        Map<String, Object> exist = activityMapper.findDetail(id);
        if (exist == null) {
            return Result.fail(404, "活动不存在");
        }
        String err = validate(dto);
        if (err != null) {
            return Result.fail(400, err);
        }

        Activity a = toEntity(dto);
        a.setId(id);
        activityMapper.update(a);
        log.info("管理员{}编辑活动{}", adminId, id);
        return Result.ok("修改成功");
    }

    /**
     * 上下架。
     * <p>
     * 上架前做可用性校验，避免"上架成功但用户端永远看不到"这类静默无效操作：
     * <ul>
     *   <li>活动不存在 / 已删除 → 404，明确原因</li>
     *   <li>活动已过结束时间 → 拒绝上架（用户端按时间窗过滤，上架也不会展示）</li>
     *   <li>适用卡类型已删除 → 拒绝上架（用户端会显示为无卡可买）</li>
     *   <li>名额已用尽 → 拒绝上架</li>
     *   <li>未到开始时间 → 允许上架，但明确告知"用户端将在开始时间后可见"</li>
     * </ul>
     * 返回体带 userVisible/message，前端据此给出准确反馈（成功与失败都有明确提示）。
     */
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Object raw = body.get("status");
        if (raw == null) {
            return Result.fail(400, "缺少 status 参数（0-下架，1-上架）");
        }
        int status;
        try {
            status = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return Result.fail(400, "status 参数不正确，应为 0（下架）或 1（上架）");
        }
        if (status != 0 && status != 1) {
            return Result.fail(400, "status 参数不正确，应为 0（下架）或 1（上架）");
        }

        Map<String, Object> act = activityMapper.findDetail(id);
        if (act == null) {
            return Result.fail(404, "活动不存在，请刷新列表后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = toDateTime(act.get("startTime"));
        LocalDateTime end = toDateTime(act.get("endTime"));

        if (status == 1) {
            // 上架前的必填/可用性校验：任何一条不满足都返回具体原因
            if (end != null && end.isBefore(now)) {
                return Result.fail(400, "上架失败：活动已于 " + fmt(end) + " 结束，用户端不会展示。请先编辑活动时间再上架");
            }
            if (act.get("cardTypeName") == null) {
                return Result.fail(400, "上架失败：适用卡类型已被删除，请重新选择卡类型");
            }
            Integer quotaTotal = toInteger(act.get("quotaTotal"));
            int quotaUsed = toInt(act.get("quotaUsed"));
            if (quotaTotal != null && quotaUsed >= quotaTotal) {
                return Result.fail(400, "上架失败：活动名额已用尽（" + quotaUsed + "/" + quotaTotal + "），请先调整名额");
            }
        }

        int rows = activityMapper.updateStatus(id, status);
        if (rows == 0) {
            return Result.fail(404, "活动不存在或已被删除，请刷新列表后重试");
        }

        boolean userVisible;
        String message;
        if (status == 1) {
            if (start != null && start.isAfter(now)) {
                userVisible = false;
                message = "已上架；活动将于 " + fmt(start) + " 开始，用户端在开始时间之后才会展示";
            } else {
                userVisible = true;
                message = "已上架，用户端立即可见";
            }
        } else {
            userVisible = false;
            message = "已下架，用户端不再展示该活动";
        }

        log.info("管理员{}将活动{}置为{}（用户端可见={}）", adminId, id, status == 1 ? "上架" : "下架", userVisible);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", status);
        data.put("userVisible", userVisible);
        data.put("message", message);
        return Result.ok(data, message);
    }

    /** 删除（软删） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        int rows = activityMapper.softDelete(id);
        if (rows == 0) {
            return Result.fail(404, "活动不存在");
        }
        log.warn("管理员{}删除活动{}", adminId, id);
        return Result.ok("已删除");
    }

    // ==================== 私有工具 ====================

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(TIME_FMT);
    }

    /** MyBatis 查询 Map 结果里的时间字段可能是 LocalDateTime / Timestamp / 字符串，统一转换 */
    private LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer toInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object v) {
        Integer i = toInteger(v);
        return i == null ? 0 : i;
    }

    private String validate(ActivityDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            return "活动标题不能为空";
        }
        if (dto.getCardTypeId() == null) {
            return "请选择适用卡类型";
        }
        if (cardTypeMapper.findById(dto.getCardTypeId()) == null) {
            return "卡类型不存在";
        }
        BigDecimal d = dto.getDiscount();
        if (d == null || d.compareTo(BigDecimal.ZERO) <= 0 || d.compareTo(BigDecimal.ONE) >= 0) {
            return "折扣率需在 0 与 1 之间（如 0.9 表示 9 折）";
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            return "请填写活动开始与结束时间";
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            return "结束时间必须晚于开始时间";
        }
        if (dto.getQuotaTotal() != null && dto.getQuotaTotal() < 0) {
            return "活动名额不能为负数";
        }
        return null;
    }

    private Activity toEntity(ActivityDTO dto) {
        Activity a = new Activity();
        a.setTitle(dto.getTitle().trim());
        a.setSubtitle(dto.getSubtitle());
        a.setCoverUrl(dto.getCoverUrl());
        a.setContent(dto.getContent());
        a.setCardTypeId(dto.getCardTypeId());
        a.setDiscount(dto.getDiscount());
        a.setQuotaTotal(dto.getQuotaTotal());
        a.setStartTime(dto.getStartTime());
        a.setEndTime(dto.getEndTime());
        a.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        return a;
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

    /** 活动新增/编辑请求体 */
    @Data
    public static class ActivityDTO {
        private String title;
        private String subtitle;
        private String coverUrl;
        private String content;
        private Long cardTypeId;
        private BigDecimal discount;
        private Integer quotaTotal;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer status;
    }

}
