package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.Arbitration;
import com.xiaoshan.fitness.entity.Complaint;
import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.mapper.GovernanceMapper;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投诉与平台仲裁业务。
 * <p>
 * 投诉状态机：
 * <pre>
 *   PENDING 待受理 ──受理──▶ PROCESSING 处理中 ──回复解决──▶ RESOLVED 已解决 ──关闭──▶ CLOSED
 *                                  │
 *                                  └──升级仲裁──▶ ARBITRATING（仲裁单 INVESTIGATING→RULING→DONE）
 *                                                                      └─裁决后投诉置 RESOLVED
 * </pre>
 * 一次投诉至多一条仲裁（DB 唯一键兜底 + 状态校验）。
 * 每次关键状态变更都给用户发站内消息；所有入参类型走白名单，防止非法状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final GovernanceMapper governanceMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final Set<String> TYPES = Set.of("SERVICE", "ORDER", "ENTRY", "CARD", "OTHER");
    public static final Set<String> BIZ_TYPES = Set.of("ORDER", "MEMBERSHIP", "ENTRY", "STORE");
    public static final Set<String> ARB_RESULTS = Set.of("SUPPORT_USER", "SUPPORT_PLATFORM", "PARTIAL");

    private static final int PAGE_SIZE_MAX = 50;

    // ==================== 用户端 ====================

    /** 提交投诉 */
    public Complaint submit(Long userId, String type, String title, String content,
                            List<String> images, String bizType, String bizId,
                            Long storeId, String contactPhone) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        String t = (type == null || !TYPES.contains(type)) ? "OTHER" : type;
        if (title == null || title.isBlank()) throw new BusinessException("标题不能为空");
        if (content == null || content.isBlank()) throw new BusinessException("投诉内容不能为空");
        if (title.length() > 100) throw new BusinessException("标题不能超过100字");
        if (content.length() > 1000) throw new BusinessException("投诉内容不能超过1000字");
        if (bizType != null && !bizType.isBlank() && !BIZ_TYPES.contains(bizType)) {
            throw new BusinessException("关联业务类型不合法");
        }
        String imagesJson = null;
        if (images != null && !images.isEmpty()) {
            if (images.size() > 6) throw new BusinessException("凭证图片最多6张");
            try {
                imagesJson = MAPPER.writeValueAsString(images);
            } catch (Exception ignored) {
                imagesJson = null;
            }
        }

        Complaint c = new Complaint();
        c.setId(idGenerator.nextId());
        c.setUserId(userId);
        c.setType(t);
        c.setTitle(title.trim());
        c.setContent(content.trim());
        c.setImagesJson(imagesJson);
        c.setBizType((bizType == null || bizType.isBlank()) ? null : bizType);
        c.setBizId(bizId);
        c.setStoreId(storeId);
        // 入库存完整手机号：脱敏只在用户端查询输出时做，管理端需要完整号码联系用户
        c.setContactPhone(contactPhone == null ? null : contactPhone.trim());
        c.setStatus("PENDING");
        governanceMapper.insertComplaint(c);
        log.info("用户{}提交投诉 id={} type={}", userId, c.getId(), t);
        return c;
    }

    /** 我的投诉列表（手机号脱敏，仅展示用） */
    public Map<String, Object> myList(Long userId, int page, int size) {
        int[] ps = normalizePage(page, size);
        List<Complaint> list = governanceMapper.listMyComplaints(userId, ps[0], ps[1]);
        list.forEach(this::maskComplaintPhone);
        long total = governanceMapper.countMyComplaints(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /** 用户查看详情（仅本人，手机号脱敏） */
    public Map<String, Object> detailForUser(Long userId, Long id) {
        Complaint c = mustOwn(userId, id);
        maskComplaintPhone(c);
        return toDetail(c);
    }

    // ==================== 管理端：投诉处理 ====================

    public Map<String, Object> adminList(String status, String type, int page, int size) {
        int[] ps = normalizePage(page, size);
        List<Complaint> list = governanceMapper.adminListComplaints(
                blankToNull(status), blankToNull(type), ps[0], ps[1]);
        long total = governanceMapper.adminCountComplaints(blankToNull(status), blankToNull(type));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    public Map<String, Object> adminDetail(Long id) {
        Complaint c = mustGet(id);
        return toDetail(c);
    }

    /** 受理：PENDING → PROCESSING */
    @Transactional
    public void accept(Long adminId, Long id) {
        Complaint c = mustGet(id);
        if (!"PENDING".equals(c.getStatus())) {
            throw new BusinessException(409, "该投诉已受理，请勿重复操作");
        }
        governanceMapper.updateComplaintHandle(id, "PROCESSING", null, adminId);
        notifyUser(c.getUserId(), "投诉已受理",
                "您的投诉「" + c.getTitle() + "」已受理，平台正在核实处理，请留意后续回复。", id);
    }

    /** 回复并解决：PROCESSING/ARBITRATING → RESOLVED */
    @Transactional
    public void resolve(Long adminId, Long id, String reply) {
        if (reply == null || reply.isBlank()) throw new BusinessException("处理回复不能为空");
        if (reply.length() > 1000) throw new BusinessException("回复不能超过1000字");
        Complaint c = mustGet(id);
        if ("RESOLVED".equals(c.getStatus()) || "CLOSED".equals(c.getStatus())) {
            throw new BusinessException(409, "该投诉已完结");
        }
        governanceMapper.updateComplaintHandle(id, "RESOLVED", reply.trim(), adminId);
        notifyUser(c.getUserId(), "投诉处理结果",
                "您的投诉「" + c.getTitle() + "」已处理：" + reply.trim(), id);
    }

    /** 关闭：RESOLVED → CLOSED（归档，不可再升级） */
    @Transactional
    public void close(Long adminId, Long id) {
        Complaint c = mustGet(id);
        if (!"RESOLVED".equals(c.getStatus())) {
            throw new BusinessException(409, "仅已解决的投诉可以关闭归档");
        }
        governanceMapper.updateComplaintStatus(id, "CLOSED");
    }

    // ==================== 管理端：仲裁 ====================

    /** 升级仲裁：PROCESSING → ARBITRATING，同时生成仲裁单 */
    @Transactional
    public Arbitration escalate(Long adminId, Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("升级仲裁原因不能为空");
        if (reason.length() > 500) throw new BusinessException("升级原因不能超过500字");
        Complaint c = mustGet(id);
        if ("CLOSED".equals(c.getStatus())) throw new BusinessException(409, "已关闭的投诉不能升级仲裁");
        if ("ARBITRATING".equals(c.getStatus())
                || governanceMapper.findArbitrationByComplaint(id) != null) {
            throw new BusinessException(409, "该投诉已在仲裁流程中");
        }
        Arbitration a = new Arbitration();
        a.setId(idGenerator.nextId());
        a.setComplaintId(id);
        a.setUserId(c.getUserId());
        a.setReason(reason.trim());
        a.setStatus("INVESTIGATING");
        governanceMapper.insertArbitration(a);
        governanceMapper.updateComplaintStatus(id, "ARBITRATING");
        notifyUser(c.getUserId(), "投诉进入平台仲裁",
                "您的投诉「" + c.getTitle() + "」已升级至平台仲裁，调查完成后会向您反馈裁决结果。", id);
        log.info("投诉{}升级仲裁 arbId={} operator={}", id, a.getId(), adminId);
        return a;
    }

    public Map<String, Object> arbitrationList(String status, int page, int size) {
        int[] ps = normalizePage(page, size);
        List<Arbitration> list = governanceMapper.adminListArbitrations(
                blankToNull(status), ps[0], ps[1]);
        long total = governanceMapper.adminCountArbitrations(blankToNull(status));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * 仲裁裁决：仲裁单 → DONE，联动投诉 → RESOLVED。
     *
     * @param result             SUPPORT_USER / SUPPORT_PLATFORM / PARTIAL
     * @param decision           裁决说明
     * @param compensationAmount 补偿金额（可空）
     */
    @Transactional
    public void rule(Long adminId, Long arbitrationId, String result, String decision,
                     BigDecimal compensationAmount) {
        if (!ARB_RESULTS.contains(result)) throw new BusinessException("裁决结果不合法");
        if (decision == null || decision.isBlank()) throw new BusinessException("裁决说明不能为空");
        if (decision.length() > 1000) throw new BusinessException("裁决说明不能超过1000字");
        if (compensationAmount != null && compensationAmount.signum() < 0) {
            throw new BusinessException("补偿金额不能为负");
        }
        Arbitration a = governanceMapper.findArbitrationById(arbitrationId);
        if (a == null) throw new BusinessException(404, "仲裁单不存在");
        if ("DONE".equals(a.getStatus())) throw new BusinessException(409, "该仲裁已裁决");

        a.setStatus("DONE");
        a.setResult(result);
        a.setDecision(decision.trim());
        a.setCompensationAmount(compensationAmount);
        a.setArbitratorAdminId(adminId);
        governanceMapper.completeArbitration(a);

        // 联动投诉完结（附带裁决回复）
        String reply = "【平台仲裁】" + decision.trim()
                + (compensationAmount != null && compensationAmount.signum() > 0
                    ? " 补偿金额：" + compensationAmount.stripTrailingZeros().toPlainString() + " 元。" : "");
        governanceMapper.updateComplaintHandle(a.getComplaintId(), "RESOLVED", reply, adminId);

        Complaint c = governanceMapper.findComplaintById(a.getComplaintId());
        String title = c == null ? "仲裁结果通知" : c.getTitle();
        notifyUser(a.getUserId(), "平台仲裁结果", "您的投诉「" + title + "」已完成仲裁：" + reply,
                a.getComplaintId());
        log.info("仲裁{}裁决完成 result={} operator={}", arbitrationId, result, adminId);
    }

    // ==================== 工具 ====================

    private Map<String, Object> toDetail(Complaint c) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaint", c);
        data.put("arbitration", governanceMapper.findArbitrationByComplaint(c.getId()));
        return data;
    }

    private Complaint mustGet(Long id) {
        Complaint c = governanceMapper.findComplaintById(id);
        if (c == null) throw new BusinessException(404, "投诉不存在");
        return c;
    }

    private Complaint mustOwn(Long userId, Long id) {
        Complaint c = mustGet(id);
        if (!userId.equals(c.getUserId())) throw new BusinessException(403, "无权查看该投诉");
        return c;
    }

    private int[] normalizePage(int page, int size) {
        int p = Math.max(1, page);
        int s = size <= 0 ? 10 : Math.min(size, PAGE_SIZE_MAX);
        return new int[]{(p - 1) * s, s};
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String p = phone.trim();
        if (p.length() < 7) return p;
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    /** 用户端展示时对投诉上的联系电话脱敏（直接改 entity，随请求序列化，不回写 DB） */
    private void maskComplaintPhone(Complaint c) {
        c.setContactPhone(maskPhone(c.getContactPhone()));
    }

    /** 站内消息通知投诉人（type=COMPLAINT，前端 Notifications.vue 有对应图标/配色） */
    private void notifyUser(Long userId, String title, String content, Long refId) {
        try {
            Message msg = new Message();
            msg.setId(idGenerator.nextId());
            msg.setUserId(userId);
            msg.setType("COMPLAINT");
            msg.setTitle(title);
            msg.setContent(truncate(content, 480));
            msg.setRefId(refId);
            messageMapper.insert(msg);
            notificationPushService.push(msg);
        } catch (Exception e) {
            log.error("投诉状态变更消息发送失败 complaintRef={}", refId, e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
