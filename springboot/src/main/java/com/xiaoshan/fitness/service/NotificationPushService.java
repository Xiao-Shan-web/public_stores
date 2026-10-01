package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统通知推送服务
 * <p>
 * 在系统通知落库后调用 push()，通过 WebSocket 实时推送给目标用户。
 * 推送目的地：/user/{user:userId}/queue/notification
 * <p>
 * 若用户不在线，消息已在数据库中，用户下次拉取列表时可见，未读数以数据库为准。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 推送系统通知给用户
     *
     * @param msg 已落库的 Message（含 id / type / title / content / refId / createdAt）
     */
    public void push(Message msg) {
        if (msg == null || msg.getUserId() == null) return;
        try {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", msg.getId());
            vo.put("userId", msg.getUserId());
            vo.put("type", msg.getType());
            vo.put("title", msg.getTitle());
            vo.put("content", msg.getContent());
            vo.put("refId", msg.getRefId());
            // TINYINT(1) 统一以 0/1 整型下发，前端用 Number(isRead) === 1 判断，避免布尔误判
            vo.put("isRead", msg.getIsRead() == null ? 0 : msg.getIsRead());
            vo.put("createdAt", msg.getCreatedAt() != null
                    ? msg.getCreatedAt().format(TIME_FORMATTER) : null);
            String payload = objectMapper.writeValueAsString(vo);
            messagingTemplate.convertAndSendToUser(
                    "user:" + msg.getUserId(), "/queue/notification", payload);
            log.debug("系统通知推送：用户{} type={} id={}", msg.getUserId(), msg.getType(), msg.getId());
        } catch (Exception e) {
            log.error("系统通知推送失败：用户{} msgId={}", msg.getUserId(), msg.getId(), e);
        }
    }
}
