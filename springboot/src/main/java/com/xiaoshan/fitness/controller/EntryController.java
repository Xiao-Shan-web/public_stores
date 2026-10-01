package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.EntryRecord;
import com.xiaoshan.fitness.entity.FaceFeature;
import com.xiaoshan.fitness.entity.Membership;
import com.xiaoshan.fitness.mapper.EntryRecordMapper;
import com.xiaoshan.fitness.mapper.FaceFeatureMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.service.FaceService;
import com.xiaoshan.fitness.service.FaceServiceException;
import com.xiaoshan.fitness.service.SmsService;
import com.xiaoshan.fitness.service.RiskControlService;
import com.xiaoshan.fitness.util.DistributedLock;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 到店核销接口（管理端/门店端）
 * <p>
 * 流程：扫描/上传人脸图片 → FaceService.searchFace 匹配 user_id
 *       → 一致性守门（命中用户在 face_features 中确实登记过人脸，防止底库残留被误放行）
 *       → 校验会员卡（ACTIVE + 按天卡 end_time>now / 按次卡 remaining_times>0）
 *       → 插入 entry_records（按次卡扣减 remaining_times）
 * <p>
 * 识别服务由 face.mode 切换：mock（缺省，本地图片哈希比对）/
 * baidu（百度 AI 人脸 V3 search，score ≥ 阈值才匹配 userId）。
 */
@RestController
@RequestMapping("/api/v1/admin/entry")
@RequiredArgsConstructor
@Slf4j
public class EntryController {

    private final JwtUtil jwtUtil;
    private final FaceService faceService;
    private final FaceFeatureMapper faceFeatureMapper;
    private final MembershipMapper membershipMapper;
    private final EntryRecordMapper entryRecordMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DistributedLock distributedLock;
    private final SmsService smsService;
    private final RiskControlService riskControlService;

    /**
     * 刷脸核销
     * POST /api/v1/admin/entry/verify  body: { imageBase64 }
     * <p>
     * 并发互斥：以被核销用户的 userId 为维度加 Redis 分布式锁（TTL 10 秒），
     * 防止同一用户被并发核销导致次卡剩余次数被重复扣减或写入多条成功记录。
     * 锁失败抛 BusinessException(429) 由全局异常处理返回统一响应。
     * <p>
     * 兜底机制：次卡扣减 SQL 带 {@code remaining_times > 0} 条件，防止并发场景下
     * 次数变负；@Transactional 保证核销记录与扣减原子提交。
     */
    @PostMapping("/verify")
    @Transactional
    public Result<Map<String, Object>> verify(@RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        Long operatorId = currentAdminId(request);
        if (operatorId == null) {
            return Result.fail("管理员未登录或登录已过期");
        }

        String imageBase64 = body.get("imageBase64") == null ? null : body.get("imageBase64").toString().trim();
        if (imageBase64 != null && imageBase64.contains(",")) {
            int idx = imageBase64.indexOf(",");
            imageBase64 = imageBase64.substring(idx + 1);
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            return Result.fail("人脸图片不能为空");
        }

        // 1. 人脸搜索：当前人脸图片在人脸底库中匹配 userId
        //    null = 正常未匹配；FaceServiceException = 服务不可用/无人脸/遮挡等，统一按失败处理绝不放行
        Long userId;
        try {
            userId = faceService.searchFace(imageBase64);
        } catch (FaceServiceException e) {
            log.warn("核销失败：人脸识别服务异常 reason={}", e.getMessage());
            recordEntry(null, null, false, e.getMessage());
            return Result.fail("人脸审核失败：" + e.getMessage());
        }
        if (userId == null) {
            log.warn("核销失败：人脸比对未通过");
            recordEntry(null, null, false, "人脸不匹配");
            return Result.fail("人脸审核失败：人脸不匹配，请重试或联系管理员");
        }

        // 2. 一致性守门：识别底库命中的用户必须是「系统登记过人脸」的用户。
        //    底库（本地哈希 / 百度人脸组 / SQLite 向量库）与 face_features 是两套存储，
        //    注销人脸、切换 face.mode、手工清库都可能让底库残留可匹配的人脸；
        //    不校验就会出现"无登记记录的人也能刷脸核销"，且状态不一致完全静默。
        //    这里一律按识别失败处理（fail-closed），绝不放行。
        Optional<FaceFeature> featureOpt = faceFeatureMapper.findByUserId(userId);
        if (featureOpt.isEmpty()) {
            // 底库命中但系统无登记记录：底库残留（注销/模式切换后未清理）
            log.warn("核销失败：人脸底库命中 userId={}，但无登记记录（底库残留）", userId);
            recordEntry(userId, null, false, "未录入人脸（底库残留）");
            return Result.fail("人脸审核失败：该用户未录入人脸，请先录入");
        }
        FaceFeature feature = featureOpt.get();
        if (feature.getFaceToken() == null || feature.getFaceToken().isBlank()) {
            // 有登记行但 face_token 为空：数据不完整
            log.warn("核销失败：userId={} 的 face_token 为空", userId);
            recordEntry(userId, null, false, "人脸登记信息不完整（face_token 为空）");
            return Result.fail("人脸审核失败：人脸登记信息不完整，请重新录入人脸");
        }
        if (!faceService.canMatch(feature)) {
            // 登记记录存在但与当前识别模式不匹配（如 mock 录入后切到 smartjava）
            log.warn("核销失败：userId={} 人脸登记模式不匹配（faceToken={}）",
                    userId, feature.getFaceToken());
            recordEntry(userId, null, false, "人脸识别模式已变更，旧数据不兼容");
            return Result.fail("人脸审核失败：人脸识别模式已变更，旧的人脸数据不兼容，请重新录入人脸");
        }
        log.info("核销流程：人脸匹配成功 userId={}", userId);

        // 3. 加分布式锁：以被核销用户为维度互斥，防止同一用户被并发核销
        //    锁 TTL=10s 覆盖单次核销事务（一般 < 1s）+ GC/网络抖动余量
        final Long uid = userId;
        final Long opId = operatorId;
        return distributedLock.executeWithLock(
                "entry:verify:" + userId,
                java.time.Duration.ofSeconds(10),
                () -> doVerify(uid, opId)
        );
    }

    /**
     * 核销核心逻辑（在分布式锁内执行）：
     * 查会员卡 → 状态/有效期校验 → 写入核销记录 → 按次卡扣减次数。
     */
    private Result<Map<String, Object>> doVerify(Long userId, Long operatorId) {
        // 入场核销仅认普通卡（NORMAL，私教课卡 PT 不参与入场）：
        // 优先 ACTIVE 普通卡（全店通用优先，其次单店卡）；无 ACTIVE 则取最新普通卡给失败原因
        Optional<Membership> activeOpt = membershipMapper.findActiveNormalByUserId(userId);
        Optional<Membership> latestNormalOpt = membershipMapper.findLatestNormalByUserId(userId);
        if (activeOpt.isEmpty() && latestNormalOpt.isEmpty()) {
            // 无任何普通卡：区分"仅持有私教课卡"与"完全无卡"
            boolean hasAnyCard = membershipMapper.findLatestByUserIdWithCardType(userId).isPresent();
            if (hasAnyCard) {
                log.warn("核销失败：userId={} 仅持有私教课卡，无可用普通卡", userId);
                recordEntry(userId, null, false, "无可用的普通会员卡（私教课卡不可入场）");
                return Result.fail("无可用的普通会员卡（私教课卡不可入场）");
            }
            log.warn("核销失败：userId={} 未购买任何会员卡", userId);
            recordEntry(userId, null, false, "未购买会员卡");
            return Result.fail("会员卡不存在");
        }

        // 有 ACTIVE 普通卡 → 用它核销；无 ACTIVE → 用最新普通卡做状态校验（返回明确失败原因）
        Membership m = activeOpt.orElse(latestNormalOpt.get());
        log.info("核销流程：使用会员卡 id={} status={} cardType={} remainingTimes={} endTime={}",
                m.getId(), m.getStatus(), m.getCardType(), m.getRemainingTimes(), m.getEndTime());

        // 3. 校验会员卡状态
        if (!"ACTIVE".equals(m.getStatus())) {
            String reason;
            if ("UNACTIVATED".equals(m.getStatus())) {
                reason = "会员卡未激活";
            } else if ("DISABLED".equals(m.getStatus())) {
                reason = "会员卡已停用";
            } else if ("EXPIRED".equals(m.getStatus())) {
                reason = "会员卡已过期";
            } else {
                reason = "会员卡状态异常：" + m.getStatus();
            }
            log.warn("核销失败：userId={} 卡状态={} reason={}", userId, m.getStatus(), reason);
            recordEntry(userId, m.getId(), false, reason);
            return Result.fail(reason, buildMembershipVO(m, userId));
        }

        // 4. 校验有效期 / 剩余次数
        LocalDateTime now = LocalDateTime.now();
        boolean isTimesCard = m.getRemainingTimes() != null;
        if (isTimesCard) {
            // 按次卡：剩余次数 > 0
            if (m.getRemainingTimes() <= 0) {
                log.warn("核销失败：userId={} 次卡剩余次数={}", userId, m.getRemainingTimes());
                recordEntry(userId, m.getId(), false, "剩余次数不足");
                return Result.fail("剩余次数不足", buildMembershipVO(m, userId));
            }
        } else {
            // 按天卡：end_time > now
            if (m.getEndTime() == null || m.getEndTime().isBefore(now)) {
                log.warn("核销失败：userId={} 天卡已过期 endTime={} now={}", userId, m.getEndTime(), now);
                recordEntry(userId, m.getId(), false, "会员卡已过期");
                return Result.fail("会员卡已过期", buildMembershipVO(m, userId));
            }
        }

        // 5. 写入核销成功记录（核销时间取 created_at）
        Long recordId = recordEntry(userId, m.getId(), true, null);

        // 6. 按次卡扣减一次
        if (isTimesCard) {
            membershipMapper.deductRemainingTimes(m.getId());
            m.setRemainingTimes(m.getRemainingTimes() - 1);
        }

        log.info("核销成功：userId={}，会员卡={}，记录ID={}，操作员={}",
                userId, m.getId(), recordId, operatorId);

        // 7. 关键通知短信：核销成功（异步发送，失败不影响核销主流程）
        String cardName = m.getCardTypeName() != null ? m.getCardTypeName() : m.getCardType();
        String timesPart = isTimesCard ? ("，剩余" + m.getRemainingTimes() + "次") : "";
        smsService.notifyEntrySuccess(userId, cardName, timesPart,
                recordId == null ? null : String.valueOf(recordId));

        // 8. 风控检测：高频核销 / 夜间核销（内部异常自吞，不影响核销）
        riskControlService.recordUserEntry(userId, null, m.getStoreId());

        Map<String, Object> data = buildMembershipVO(m, userId);
        data.put("entryRecordId", recordId);
        data.put("entryTime", now);
        data.put("result", "SUCCESS");
        return Result.ok(data, "核销成功");
    }

    /**
     * 写入一条核销记录（成功/失败都写）。记录写入失败不影响核销主流程。
     *
     * @return 记录ID；写入失败返回 null
     */
    private Long recordEntry(Long userId, Long membershipId, boolean success, String failReason) {
        try {
            EntryRecord record = new EntryRecord();
            record.setId(idGenerator.nextId());
            record.setUserId(userId);
            record.setMembershipId(membershipId);
            record.setCheckType("FACE");
            record.setResult(success ? "SUCCESS" : "FAILED");
            record.setFailReason(success ? null : failReason);
            entryRecordMapper.insert(record);
            return record.getId();
        } catch (Exception e) {
            log.error("写入核销记录失败：userId={}, membershipId={}, success={}", userId, membershipId, success, e);
            return null;
        }
    }

    // ==================== 私有辅助 ====================

    private Map<String, Object> buildMembershipVO(Membership m, Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("membershipId", m.getId());
        data.put("cardNo", m.getCardNo());
        data.put("cardName", m.getCardTypeName() != null ? m.getCardTypeName() : m.getCardType());
        data.put("category", m.getCategory() != null ? m.getCategory() : "NORMAL");
        data.put("scope", m.getScope() != null ? m.getScope() : "ALL_STORE");
        data.put("storeName", m.getStoreName());
        data.put("status", m.getStatus());
        data.put("remainingTimes", m.getRemainingTimes());
        data.put("startTime", m.getStartTime());
        data.put("endTime", m.getEndTime());
        return data;
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

}
