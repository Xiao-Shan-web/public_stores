package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.Message;
import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.mapper.MessageMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 管理员系统通知服务
 * <p>
 * 管理员给「全部用户」或「指定用户」发送通知：
 * - 每个接收用户落一条 messages 记录（独立 is_read 已读状态）
 * - 同一次发送共享 batch_id，管理端列表按 batch_id 聚合、整批删除
 * - scope 记录发送范围（ALL / SPECIFIED）
 * - 落库后通过 {@link NotificationPushService} 走 WebSocket 实时推送给在线用户
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationPushService notificationPushService;

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_SPECIFIED = "SPECIFIED";
    /** 分人群发送：按持卡类型 / 门店筛选接收人 */
    public static final String SCOPE_SEGMENT = "SEGMENT";

    /** 批量插入分片大小，避免单条 SQL 过大 */
    private static final int INSERT_CHUNK = 500;

    /**
     * 创建并发送通知
     *
     * @param adminId   发送管理员ID
     * @param type      通知类型 SYSTEM/ACTIVITY/VERIFY/EXPIRE
     * @param title     标题
     * @param content   内容
     * @param scope     ALL / SPECIFIED
     * @param receivers 指定发送时的接收者标识（用户ID 或 手机号），可空
     * @return receiverCount 成功发送人数、scope 实际范围、batchId 批次、missing 未匹配标识
     */
    @Transactional
    public Map<String, Object> send(Long adminId, String type, String title,
                                    String content, String scope, List<String> receivers) {
        return send(adminId, type, title, content, scope, receivers, null, null);
    }

    /**
     * 创建并发送通知（支持分人群）
     *
     * @param adminId    发送管理员ID
     * @param type       通知类型 SYSTEM/ACTIVITY/VERIFY/EXPIRE
     * @param title      标题
     * @param content    内容
     * @param scope      ALL / SPECIFIED / SEGMENT
     * @param receivers  指定发送时的接收者标识（用户ID 或 手机号），可空
     * @param cardTypeId 分人群：持该卡类型用户（可空）
     * @param storeId    分人群：持该门店卡用户（可空）
     * @return receiverCount / scope / batchId / missing
     */
    @Transactional
    public Map<String, Object> send(Long adminId, String type, String title,
                                    String content, String scope, List<String> receivers,
                                    Long cardTypeId, Long storeId) {
        Long batchId = idGenerator.nextId();
        List<String> missing = new ArrayList<>();
        List<Long> userIds;
        String actualScope;

        if (SCOPE_ALL.equalsIgnoreCase(scope)) {
            userIds = userMapper.findAllIds();
            actualScope = SCOPE_ALL;
        } else if (SCOPE_SEGMENT.equalsIgnoreCase(scope)) {
            // 分人群：按卡类型 / 门店筛选（两者均为空则退化为全部用户）
            if (cardTypeId == null && storeId == null) {
                userIds = userMapper.findAllIds();
                actualScope = SCOPE_ALL;
            } else {
                userIds = userMapper.findIdsBySegment(cardTypeId, storeId);
                actualScope = SCOPE_SEGMENT;
            }
        } else {
            actualScope = SCOPE_SPECIFIED;
            Set<Long> resolved = new LinkedHashSet<>();
            if (receivers != null) {
                for (String raw : receivers) {
                    if (raw == null) {
                        continue;
                    }
                    String key = raw.trim();
                    if (key.isEmpty()) {
                        continue;
                    }
                    Long uid = resolveUserId(key);
                    if (uid != null) {
                        resolved.add(uid);
                    } else {
                        missing.add(key);
                    }
                }
            }
            userIds = new ArrayList<>(resolved);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", actualScope);
        result.put("batchId", batchId);
        result.put("missing", missing);
        result.put("receiverCount", 0);

        if (userIds.isEmpty()) {
            log.warn("管理员{}发送通知[{}]无有效接收者，missing={}", adminId, type, missing);
            return result;
        }

        // 组装每个接收用户的消息记录
        List<Message> batch = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            Message m = new Message();
            m.setId(idGenerator.nextId());
            m.setUserId(uid);
            m.setSenderId(adminId);
            m.setBatchId(batchId);
            m.setScope(actualScope);
            // 分人群发送时落库筛选条件，便于管理端列表回溯「这批发给了谁」
            if (SCOPE_SEGMENT.equals(actualScope)) {
                m.setCardTypeId(cardTypeId);
                m.setStoreId(storeId);
            }
            m.setType(type);
            m.setTitle(title);
            m.setContent(content);
            m.setIsRead(0);
            batch.add(m);
        }

        // 分片批量落库
        for (int i = 0; i < batch.size(); i += INSERT_CHUNK) {
            List<Message> part = batch.subList(i, Math.min(i + INSERT_CHUNK, batch.size()));
            messageMapper.insertBatch(part);
        }

        // WebSocket 实时推送（在线用户立即收到 /user/queue/notification；离线用户登录后拉取）
        for (Message m : batch) {
            notificationPushService.push(m);
        }

        log.info("管理员{}发送通知[{}|{}] -> {} 个用户，batchId={}，未匹配={}",
                adminId, type, actualScope, userIds.size(), batchId, missing);

        result.put("receiverCount", userIds.size());
        return result;
    }

    /**
     * 管理端：已发通知批次列表（分页，按发送时间倒序）
     */
    public Map<String, Object> listBatches(Long adminId, String type, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<Map<String, Object>> list = messageMapper.findAdminBatches(adminId, type, offset, size);
        long total = messageMapper.countAdminBatches(adminId, type);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * 管理端：按批次删除整组通知
     *
     * @return 删除的记录条数
     */
    @Transactional
    public int deleteBatch(Long adminId, Long batchId) {
        int rows = messageMapper.deleteByBatchId(batchId, adminId);
        log.info("管理员{}删除通知批次{}，共删除{}条", adminId, batchId, rows);
        return rows;
    }

    /**
     * 解析接收者标识：纯数字先按用户ID、再按手机号；非数字按手机号
     */
    private Long resolveUserId(String key) {
        if (key.matches("\\d+")) {
            Optional<User> byId = userMapper.findById(Long.parseLong(key));
            if (byId.isPresent()) {
                return byId.get().getId();
            }
            Optional<User> byPhone = userMapper.findByPhone(key);
            if (byPhone.isPresent()) {
                return byPhone.get().getId();
            }
            return null;
        }
        return userMapper.findByPhone(key).map(User::getId).orElse(null);
    }
}
