package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.annotation.Idempotent;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.mapper.EntryRecordMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.service.MembershipActivationService;
import com.xiaoshan.fitness.util.DistributedLock;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户端业务接口
 * 会员卡 / 核销记录 数据均来自数据库，无任何假数据
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final JwtUtil jwtUtil;
    private final MembershipMapper membershipMapper;
    private final EntryRecordMapper entryRecordMapper;
    private final MembershipActivationService membershipActivationService;
    private final DistributedLock distributedLock;

    private static final Map<String, String> CARD_NAME_MAP = Map.of(
            "TIMES", "次卡",
            "MONTHLY", "月卡",
            "YEARLY", "年卡"
    );

    private static final Map<String, Integer> STATUS_MAP = Map.of(
            "UNACTIVATED", 0,
            "ACTIVE", 1,
            "EXPIRED", 2,
            "DISABLED", 3
    );

    /**
     * 获取当前用户的会员卡（GET /api/v1/user/card）
     * 无卡时返回 data=null，前端按"未开通"展示
     */
    @GetMapping("/card")
    public Result<Map<String, Object>> getCard(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录");
        }

        Membership m = membershipMapper.findLatestByUserIdWithCardType(userId).orElse(null);
        if (m == null) {
            return Result.ok(null);
        }
        return Result.ok(buildMemberCard(m));
    }

    /**
     * 获取当前用户全部会员卡（GET /api/v1/user/memberships）
     * 按状态优先级排序：生效中 → 未激活 → 已过期/已停用，同状态按办理时间倒序
     */
    @GetMapping("/memberships")
    public Result<List<Map<String, Object>>> getMemberships(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录");
        }
        List<Membership> all = membershipMapper.findAllByUserId(userId);
        // 按状态优先级排序：1(生效中) > 0(未激活) > 2(已过期) > 3(已停用)
        List<Map<String, Object>> list = all.stream()
                .map(this::buildMemberCard)
                .sorted((a, b) -> {
                    int sa = (int) a.get("status");
                    int sb = (int) b.get("status");
                    int pa = statusPriority(sa);
                    int pb = statusPriority(sb);
                    if (pa != pb) return pa - pb;
                    return 0; // 同优先级保持原序（SQL 已按 created_at DESC）
                })
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 用户端激活指定会员卡（UNACTIVATED → ACTIVE）
     * POST /api/v1/user/memberships/{id}/activate
     * 激活与"人脸录入"解耦：用户可先激活会员卡，再到独立页录入人脸
     * <p>
     * 幂等窗口 5 秒：防止用户连点"激活"按钮。业务幂等（已激活状态）由 DB 状态校验兜底。
     * <p>
     * 并发互斥：以 membershipId 为维度加 Redis 分布式锁（TTL 30 秒），
     * 防止用户多端/多请求并发激活同一会员卡导致状态错乱。
     * 锁失败抛 BusinessException(429) 由全局异常处理返回统一响应。
     */
    @PostMapping("/memberships/{id}/activate")
    @Idempotent(expireSeconds = 5)
    public Result<Map<String, Object>> activateMembership(@PathVariable Long id, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        return distributedLock.executeWithLock(
                "membership:activate:" + id,
                java.time.Duration.ofSeconds(30),
                () -> {
                    Map<String, Object> data = membershipActivationService.activate(id, userId);
                    boolean success = Boolean.TRUE.equals(data.get("success"));
                    return success ? Result.ok(data, (String) data.get("message"))
                                   : Result.fail((String) data.get("message"));
                }
        );
    }

    /**
     * 获取当前用户的核销记录（GET /api/v1/user/records）
     */
    @GetMapping("/records")
    public Result<Map<String, Object>> getRecords(HttpServletRequest request,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录");
        }

        int offset = Math.max(0, (page - 1) * size);
        List<Map<String, Object>> rows = entryRecordMapper.findByUserId(userId, offset, size);
        long total = entryRecordMapper.countByUserId(userId);

        List<Map<String, Object>> list = rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("cardName", row.get("cardName"));
            item.put("useTime", row.get("useTime"));
            item.put("result", row.get("result"));
            item.put("failReason", row.get("failReason"));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 从请求中解析当前用户ID
     */
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

    /**
     * 构建会员卡响应（DB → 前端 MemberCard 契约）
     * 含状态文本 statusText 与剩余天数 remainingDays（按天卡）
     */
    private Map<String, Object> buildMemberCard(Membership m) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", m.getId());
        card.put("cardNo", m.getCardNo());
        card.put("cardName", m.getCardTypeName() != null ? m.getCardTypeName()
                : CARD_NAME_MAP.getOrDefault(m.getCardType(), "会员卡"));
        int statusCode = STATUS_MAP.getOrDefault(m.getStatus(), 0);
        card.put("status", statusCode);
        card.put("statusText", statusText(statusCode));
        card.put("category", m.getCategory() != null ? m.getCategory() : "NORMAL");
        card.put("scope", m.getScope() != null ? m.getScope() : "ALL_STORE");
        card.put("storeName", m.getStoreName());
        card.put("totalTimes", m.getTotalTimes() != null ? m.getTotalTimes() : 0);
        card.put("remainTimes", m.getRemainingTimes() != null ? m.getRemainingTimes() : 0);
        // 按天卡（无 remainingTimes）：计算剩余天数；次卡返回 null
        Integer remainingDays = null;
        if (m.getRemainingTimes() == null && m.getEndTime() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(),
                    m.getEndTime().toLocalDate());
            remainingDays = (int) Math.max(0, days);
        }
        card.put("remainingDays", remainingDays);
        card.put("expireTime", m.getEndTime());
        // 激活时间 = 生效时间 = start_time（激活时写入 now）；未激活为 null，前端显示"未激活"
        card.put("startTime", m.getStartTime());
        card.put("createTime", m.getCreatedAt());
        return card;
    }

    private String statusText(int code) {
        switch (code) {
            case 0: return "未激活";
            case 1: return "有效";
            case 2: return "已过期";
            case 3: return "已停用";
            default: return "未知";
        }
    }

    /** 状态排序优先级：生效中(1) > 未激活(0) > 已过期(2) > 已停用(3) */
    private int statusPriority(int status) {
        switch (status) {
            case 1: return 0;
            case 0: return 1;
            case 2: return 2;
            case 3: return 3;
            default: return 9;
        }
    }

}
