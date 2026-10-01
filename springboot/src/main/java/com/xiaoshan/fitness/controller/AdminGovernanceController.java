package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.service.AuditLogService;
import com.xiaoshan.fitness.service.ComplaintService;
import com.xiaoshan.fitness.service.RiskControlService;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 平台治理管理端接口：投诉受理 / 平台仲裁 / 风控事件 / 操作审计。
 * 均需管理员登录（type=admin JWT）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminGovernanceController {

    private final ComplaintService complaintService;
    private final RiskControlService riskControlService;
    private final AuditLogService auditLogService;
    private final JwtUtil jwtUtil;

    // ==================== 投诉 ====================

    @GetMapping("/complaints")
    public Result<Map<String, Object>> complaintList(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      HttpServletRequest request) {
        if (currentAdminId(request) == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.adminList(status, type, page, size));
    }

    @GetMapping("/complaints/{id}")
    public Result<Map<String, Object>> complaintDetail(@PathVariable Long id, HttpServletRequest request) {
        if (currentAdminId(request) == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.adminDetail(id));
    }

    /** 受理投诉 */
    @PostMapping("/complaints/{id}/accept")
    public Result<?> accept(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        complaintService.accept(adminId, id);
        return Result.ok(null, "已受理");
    }

    /** 回复并解决 */
    @PostMapping("/complaints/{id}/resolve")
    public Result<?> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body,
                             HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        complaintService.resolve(adminId, id, str(body.get("reply")));
        return Result.ok(null, "已回复并标记解决");
    }

    /** 关闭归档 */
    @PostMapping("/complaints/{id}/close")
    public Result<?> close(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        complaintService.close(adminId, id);
        return Result.ok(null, "已关闭");
    }

    /** 升级平台仲裁 */
    @PostMapping("/complaints/{id}/arbitrate")
    public Result<?> arbitrate(@PathVariable Long id, @RequestBody Map<String, Object> body,
                               HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.escalate(adminId, id, str(body.get("reason"))),
                "已升级平台仲裁");
    }

    // ==================== 仲裁 ====================

    @GetMapping("/arbitrations")
    public Result<Map<String, Object>> arbitrationList(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       HttpServletRequest request) {
        if (currentAdminId(request) == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(complaintService.arbitrationList(status, page, size));
    }

    /**
     * 仲裁裁决
     * body: { result, decision, compensationAmount? }
     */
    @PostMapping("/arbitrations/{id}/rule")
    public Result<?> rule(@PathVariable Long id, @RequestBody Map<String, Object> body,
                          HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        complaintService.rule(adminId, id,
                str(body.get("result")), str(body.get("decision")),
                decimal(body.get("compensationAmount")));
        return Result.ok(null, "裁决已完成");
    }

    // ==================== 风控 ====================

    @GetMapping("/risk-events")
    public Result<Map<String, Object>> riskList(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String level,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                HttpServletRequest request) {
        if (currentAdminId(request) == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(riskControlService.adminList(status, level, type, page, size));
    }

    /**
     * 处置风控事件
     * body: { status: HANDLED|IGNORED, remark? }
     */
    @PostMapping("/risk-events/{id}/handle")
    public Result<?> handleRisk(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        Long adminId = currentAdminId(request);
        if (adminId == null) return Result.fail(401, "未登录或登录已过期");
        riskControlService.handle(adminId, id, str(body.get("status")), str(body.get("remark")));
        return Result.ok(null, "处置完成");
    }

    // ==================== 审计日志 ====================

    @GetMapping("/audit-logs")
    public Result<Map<String, Object>> auditList(@RequestParam(required = false) String module,
                                                 @RequestParam(required = false) Long adminId,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        if (currentAdminId(request) == null) return Result.fail(401, "未登录或登录已过期");
        return Result.ok(auditLogService.adminList(module, adminId, action, page, size));
    }

    // ==================== 工具 ====================

    private Long currentAdminId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) return null;
        if (!JwtUtil.TYPE_ADMIN.equals(jwtUtil.getTypeFromToken(token))) return null;
        return jwtUtil.getUserIdFromToken(token);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private BigDecimal decimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
